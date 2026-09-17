package edu.njust.narrativestudio;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.njust.narrativestudio.dto.*;
import edu.njust.narrativestudio.dto.RuleDtos.*;
import edu.njust.narrativestudio.service.*;
import edu.njust.narrativestudio.config.JwtService;
import edu.njust.narrativestudio.exception.BusinessException;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:progress;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:week3-schema.sql,classpath:database-extension-test.sql",
    "debug=false","logging.level.root=WARN","logging.level.org.springframework=ERROR"
})
@AutoConfigureMockMvc
class ProgressionIntegrationTest {
    @Autowired PlaytestService play;
    @Autowired PlayerProgressService progress;
    @Autowired RuleService rules;
    @Autowired ReleaseService releases;
    @Autowired StoryTransferService transfer;
    @Autowired StoryGraphService graph;
    @Autowired EndingCoverageService coverage;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    UnlockRule ending(String key) {return new UnlockRule("ENDING",null,null,key,null,null,null);}
    UnlockRule allFour() {return new UnlockRule("ALL",List.of(ending("end_a"),ending("end_b"),ending("end_c"),ending("end_d")),null,null,null,null,null);}
    @BeforeEach void fixture() throws Exception {
        for(String table:List.of("player_progress","account_action_token","test_feedback","playtest_step","playtest_session","story_release",
                "story_choice_draft","state_effect","choice_condition","story_choice","node_character","character_knowledge","character_relation",
                "character_profile","world_entry","detected_issue","story_node","state_variable","project_member","narrative_project","sys_user"))
            db.update("DELETE FROM "+table);
        db.update("INSERT INTO sys_user(id,username,password_hash,display_name) VALUES(1,'author','not-login','Author'),(2,'tester','not-login','Tester'),(3,'stranger','not-login','Stranger')");
        db.update("INSERT INTO narrative_project(id,name,owner_id) VALUES(10,'Four routes',1),(20,'Other',1)");
        db.update("INSERT INTO project_member(project_id,user_id,member_role) VALUES(10,1,'OWNER'),(10,2,'TESTER'),(20,1,'OWNER')");
        db.update("INSERT INTO story_node(id,project_id,node_key,title,node_type,is_start) VALUES(100,10,'start','Start','NORMAL',1),(101,10,'middle','Middle','NORMAL',0)");
        for(int i=0;i<5;i++) {
            db.update("INSERT INTO story_node(id,project_id,node_key,title,node_type,is_start) VALUES(?,10,?,?,'ENDING',0)",201+i,i<4?"end_"+(char)('a'+i):"hidden","Ending "+i);
            db.update("INSERT INTO story_choice(id,project_id,source_node_id,target_node_id,choice_text,sort_order,enabled) VALUES(?,10,100,?,?,?,1)",1+i,201+i,"Route "+i,i);
        }
        db.update("INSERT INTO story_choice(id,project_id,source_node_id,target_node_id,choice_text,enabled) VALUES(6,10,100,101,'Visit',1),(7,10,101,201,'Finish A',1),(8,10,101,100,'Return',1)");
        db.update("INSERT INTO state_variable(id,project_id,variable_key,display_name,value_type,initial_value,persistence_scope) VALUES(501,10,'score','Score','INTEGER','0','SESSION'),(502,10,'hidden_routes_unlocked','Flag','BOOLEAN','false','PROFILE'),(503,10,'visits','Visits','INTEGER','0','PROFILE')");
        db.update("INSERT INTO state_effect(choice_id,variable_id,operation,operand_value,sort_order) VALUES(1,502,'SET','true',0),(6,503,'ADD','1',0),(6,501,'ADD','5',1)");
        rules.replaceRules(1L,10L,100L,5L,new RulesRequest(List.of(),List.of(),allFour()));
    }
    PlaytestDtos.SessionView finish(Long user,long choice,Long release) {
        var s=release==null?play.start(user,10L):play.startRelease(user,10L,release);
        return play.advance(user,10L,s.id(),choice,0);
    }
    boolean hidden(PlaytestDtos.SessionView session) {return session.availableChoices().stream().anyMatch(c->c.id()==5L);}
    @Test void fourDistinctEndingsUnlockAcrossRestartButRepeatsDoNotCount() {
        var s=play.start(2L,10L);
        assertFalse(hidden(s));assertTrue(s.lockedChoices().stream().anyMatch(c->c.reason().contains("end_d")));
        assertThrows(BusinessException.class,()->play.advance(2L,10L,s.id(),5L,0));
        play.advance(2L,10L,s.id(),1L,0);
        for(long c:new long[]{1,1,2,3}) {
            var done=finish(2L,c,null);var restarted=play.restart(2L,10L,done.id());
            assertFalse(hidden(restarted));play.stop(2L,10L,restarted.id());
        }
        assertEquals(3,progress.get(2L,10L,null).progress().completedEndings().size());
        var fourth=finish(2L,4,null);
        var next=play.restart(2L,10L,fourth.id());assertTrue(hidden(next));
        assertEquals("true",next.state().get("hidden_routes_unlocked"));
        assertEquals("COMPLETED",play.advance(2L,10L,next.id(),5L,0).status());
    }
    @Test void profileEffectsPersistButSessionStateResetsAndAbortDoesNotCompleteEnding() {
        var start=play.start(2L,10L);
        var middle=play.advance(2L,10L,start.id(),6L,0);
        assertEquals("5",middle.state().get("score"));assertEquals("1",middle.state().get("visits"));
        play.stop(2L,10L,start.id());
        var restarted=play.restart(2L,10L,start.id());
        assertEquals("0",restarted.state().get("score"));assertEquals("1",restarted.state().get("visits"));
        assertTrue(restarted.progress().completedEndings().isEmpty());
        assertTrue(restarted.progress().visitedNodes().contains("middle"));
    }
    @Test void currentOtherUsersAndEachReleaseAreIsolatedAndFrozen() {
        var one=releases.publish(1L,10L);var two=releases.publish(1L,10L);
        for(long c=1;c<=4;c++) finish(2L,c,one.id());
        assertTrue(hidden(play.startRelease(2L,10L,one.id())));
        assertFalse(hidden(play.startRelease(2L,10L,two.id())));
        assertFalse(hidden(play.start(2L,10L)));assertFalse(hidden(play.startRelease(1L,10L,one.id())));
        rules.replaceRules(1L,10L,100L,5L,new RulesRequest(List.of(),List.of()));
        assertFalse(hidden(play.startRelease(1L,10L,one.id())));
        assertTrue(hidden(play.start(1L,10L)));
        assertThrows(BusinessException.class,()->progress.get(1L,20L,one.id()));
        assertThrows(BusinessException.class,()->progress.get(3L,10L,null));
    }
    @Test void resetRequiresNoRunningSessionAndMatchingRevisionAndDoesNotRewriteHistory() {
        var s=play.start(2L,10L);var initial=progress.get(2L,10L,null);
        assertThrows(BusinessException.class,()->progress.reset(2L,10L,null,new PlayerProgressService.Reset(initial.revision(),true)));
        play.advance(2L,10L,s.id(),1L,0);
        var history=play.steps(2L,10L,s.id(),1,100).items();
        assertThrows(BusinessException.class,()->progress.reset(2L,10L,null,new PlayerProgressService.Reset(initial.revision(),true)));
        var current=progress.get(2L,10L,null);
        progress.reset(2L,10L,null,new PlayerProgressService.Reset(current.revision(),true));
        assertTrue(progress.get(2L,10L,null).progress().completedEndings().isEmpty());
        assertEquals(history,play.steps(2L,10L,s.id(),1,100).items());
        assertEquals(Set.of("end_a"),play.get(2L,10L,s.id()).progress().completedEndings());
        var fresh=play.start(2L,10L);
        assertEquals("false",fresh.state().get("hidden_routes_unlocked"));assertFalse(hidden(fresh));
    }
    @Test void testOverrideIsExplicitOwnEditorOnlyAndNotRealCoverage() throws Exception {
        var override=new PlayerProgressService.Override(0,true,Map.of("hidden_routes_unlocked","true"),Set.of("end_a","end_b","end_c","end_d"));
        assertThrows(BusinessException.class,()->progress.override(2L,10L,null,override));
        progress.override(1L,10L,null,override);
        assertEquals(0,coverage.get(1L,10L,null).completedSessions());
        assertTrue(hidden(play.start(1L,10L)));
        assertTrue(progress.get(2L,10L,null).progress().completedEndings().isEmpty());
        mvc.perform(post("/api/projects/10/player-progress/reset").header("Authorization","Bearer "+jwt.createToken(2L,"tester"))
                .contentType("application/json").content("{\"expectedRevision\":0,\"confirm\":false}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/projects/10/player-progress")).andExpect(status().isUnauthorized());
    }
    @Test void legacyCompletedSessionsSeedOnceAndResetDoesNotResurrectThem() {
        finish(2L,1,null);finish(2L,2,null);finish(1L,3,null);
        db.update("DELETE FROM player_progress");
        assertEquals(Set.of("end_a","end_b"),progress.get(2L,10L,null).progress().completedEndings());
        progress.reset(2L,10L,null,new PlayerProgressService.Reset(0,true));
        assertTrue(progress.get(2L,10L,null).progress().completedEndings().isEmpty());
        assertTrue(play.start(2L,10L).progress().completedEndings().isEmpty());
    }
    @Test void simultaneousSessionsDoNotOverwriteEachOthersProfileUpdates() throws Exception {
        var a=play.start(2L,10L);var b=play.start(2L,10L);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var one=executor.submit(()->{gate.await();return play.advance(2L,10L,a.id(),6L,0);});
            var two=executor.submit(()->{gate.await();return play.advance(2L,10L,b.id(),6L,0);});
            gate.countDown();one.get(15,TimeUnit.SECONDS);two.get(15,TimeUnit.SECONDS);
        }
        assertEquals("2",progress.get(2L,10L,null).progress().values().get("visits"));
        assertEquals("2",play.get(2L,10L,a.id()).state().get("visits"));
    }
    @Test void duplicateAdvanceChangesProgressOnlyOnce() throws Exception {
        var s=play.start(2L,10L);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            Callable<Boolean> action=()->{gate.await();try{play.advance(2L,10L,s.id(),6L,0);return true;}catch(BusinessException ex){return false;}};
            var a=executor.submit(action);var b=executor.submit(action);gate.countDown();
            assertEquals(1,(a.get(15,TimeUnit.SECONDS)?1:0)+(b.get(15,TimeUnit.SECONDS)?1:0));
        }
        assertEquals("1",progress.get(2L,10L,null).progress().values().get("visits"));
        assertEquals(2,play.steps(2L,10L,s.id(),1,100).total());
    }
    @Test void stepInsertFailureRollsBackProfileAndSessionTogether() {
        var s=play.start(2L,10L);var before=progress.get(2L,10L,null);
        db.execute("ALTER TABLE playtest_step ADD CONSTRAINT test_fail_next_step CHECK (step_no=0)");
        try {assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->play.advance(2L,10L,s.id(),6L,0));}
        finally {db.execute("ALTER TABLE playtest_step DROP CONSTRAINT test_fail_next_step");}
        assertEquals(before,progress.get(2L,10L,null));
        assertEquals(0,play.get(2L,10L,s.id()).stepNo());
    }
    @Test void transferV2PreservesRulesAndScopesButNeverPlayerProgress() {
        for(long c=1;c<=4;c++)finish(1L,c,null);
        var document=transfer.exportStory(1L,10L);assertEquals(2,document.schemaVersion());
        var imported=transfer.importStory(1L,document);
        assertEquals(document,transfer.exportStory(1L,imported.id()));
        assertTrue(progress.get(1L,imported.id(),null).progress().completedEndings().isEmpty());
        assertTrue(play.start(1L,imported.id()).lockedChoices().stream().anyMatch(c->c.choiceText().equals("Route 4")));
    }
    @Test void stableKeysAndProfileTypesCannotBeSilentlyChanged() {
        assertThrows(BusinessException.class,()->graph.updateNode(1L,10L,201L,
                new StoryGraphDtos.NodeRequest("renamed","A",null,"ENDING",null,false,null,null)));
        var s=play.start(1L,10L);play.stop(1L,10L,s.id());
        assertThrows(BusinessException.class,()->rules.updateVariable(1L,10L,502L,new VariableRequest("new_flag","Flag","BOOLEAN","false",null,"PROFILE")));
    }
    @Test void nestedRouteAndSessionPrerequisitesApplyTogether() {
        var score=new UnlockRule("VARIABLE",null,null,null,"score","GTE","5");
        var any=new UnlockRule("ANY",List.of(ending("end_a"),ending("end_b")),null,null,null,null,null);
        var notHidden=new UnlockRule("NOT",List.of(ending("hidden")),null,null,null,null,null);
        var rule=new UnlockRule("ALL",List.of(any,score,notHidden),null,null,null,null,null);
        rules.replaceRules(1L,10L,100L,5L,new RulesRequest(List.of(),List.of(),rule));
        finish(2L,1,null);var s=play.start(2L,10L);assertFalse(hidden(s));
        play.advance(2L,10L,s.id(),6L,0);s=play.advance(2L,10L,s.id(),8L,1);assertTrue(hidden(s));
        play.advance(2L,10L,s.id(),5L,2);
        var fresh=play.start(2L,10L);play.advance(2L,10L,fresh.id(),6L,0);
        assertFalse(hidden(play.advance(2L,10L,fresh.id(),8L,1)));
    }
    @Test void thresholdCanRequireAnyTwoOfThreeDistinctEndings() {
        rules.replaceRules(1L,10L,100L,5L,new RulesRequest(List.of(),List.of(),
                new UnlockRule("AT_LEAST",List.of(ending("end_a"),ending("end_b"),ending("end_c")),2,null,null,null,null)));
        finish(2L,1,null);finish(2L,1,null);var s=play.start(2L,10L);assertFalse(hidden(s));play.stop(2L,10L,s.id());
        finish(2L,3,null);assertTrue(hidden(play.start(2L,10L)));
    }
    @Test void invalidExpressionIsRejectedAtomicallyAndLegacyRuleRemains() {
        var before=rules.getRules(1L,10L,100L,5L);
        assertThrows(BusinessException.class,()->rules.replaceRules(1L,10L,100L,5L,
                new RulesRequest(List.of(),List.of(),ending("foreign"))));
        var repeated=new UnlockRule("AT_LEAST",List.of(ending("end_a"),ending("end_a")),2,null,null,null,null);
        assertThrows(BusinessException.class,()->rules.replaceRules(1L,10L,100L,5L,new RulesRequest(List.of(),List.of(),repeated)));
        UnlockRule deep=ending("end_a");
        for(int i=0;i<10;i++) deep=new UnlockRule("NOT",List.of(deep),null,null,null,null,null);
        final var tooDeep=deep;
        assertThrows(BusinessException.class,()->rules.replaceRules(1L,10L,100L,5L,new RulesRequest(List.of(),List.of(),tooDeep)));
        assertEquals(before,rules.getRules(1L,10L,100L,5L));
    }
}
