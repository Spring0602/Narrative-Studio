package edu.njust.narrativestudio;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import edu.njust.narrativestudio.dto.RuleDtos.*;
import edu.njust.narrativestudio.dto.PlaytestDtos.*;
import edu.njust.narrativestudio.exception.BusinessException;
import edu.njust.narrativestudio.config.JwtService;
import edu.njust.narrativestudio.service.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Real Spring transactions, mapped SQL and HTTP security; isolated from the business database. */
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:week3;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:week3-schema.sql",
    "debug=false","logging.level.root=WARN","logging.level.org.springframework=ERROR"
})
@AutoConfigureMockMvc
class Week3IntegrationTest {
    @Autowired PlaytestService playtests;
    @Autowired RuleService rules;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;

    @BeforeEach
    void fixture() {
        for(String table:List.of("test_feedback","playtest_step","playtest_session","state_effect","choice_condition","story_choice",
                "node_character","character_knowledge","character_relation","character_profile","world_entry","detected_issue",
                "story_node","state_variable","project_member","narrative_project","sys_user")) db.update("DELETE FROM "+table);
        for(int id=1;id<=4;id++) db.update("INSERT INTO sys_user(id,username,password_hash,display_name) VALUES(?,?,?,?)",id,"user"+id,"not-a-login-hash","User "+id);
        db.update("INSERT INTO narrative_project(id,name,owner_id) VALUES(10,'Test',1),(20,'Other',1)");
        db.update("INSERT INTO project_member(id,project_id,user_id,member_role) VALUES(1,10,1,'OWNER'),(2,10,2,'TESTER'),(3,10,3,'TESTER'),(4,20,1,'OWNER')");
        db.update("INSERT INTO story_node(id,project_id,node_key,title,node_type,is_start) VALUES(101,10,'start','Start','NORMAL',1),(102,10,'middle','Middle','NORMAL',0),(103,10,'end','End','ENDING',0),(201,20,'foreign','Other','NORMAL',1)");
        db.update("INSERT INTO story_choice(id,project_id,source_node_id,target_node_id,choice_text,enabled) VALUES(1001,10,101,102,'Add trust',1),(1002,10,102,103,'Hidden ending',1),(1003,10,101,101,'Loop',0)");
        db.update("INSERT INTO state_variable(id,project_id,variable_key,display_name,value_type,initial_value) VALUES(501,10,'trust','Trust','INTEGER','30'),(502,20,'other','Other','INTEGER','0')");
        db.update("INSERT INTO state_effect(id,choice_id,variable_id,operation,operand_value,sort_order) VALUES(601,1001,501,'ADD','20',0)");
        db.update("INSERT INTO choice_condition(id,choice_id,variable_id,operator,expected_value,condition_group,sort_order) VALUES(701,1002,501,'GTE','50',0,0)");
    }
    @Test void reachesEndingWithReplayableSnapshots() {
        SessionView start=playtests.start(2L,10L);
        assertEquals(0,start.stepNo());assertEquals("30",start.state().get("trust"));assertEquals(List.of(1001L),start.availableChoices().stream().map(ChoiceView::id).toList());
        SessionView middle=playtests.advance(2L,10L,start.id(),1001L,0);
        assertEquals("50",middle.state().get("trust"));assertEquals(1002L,middle.availableChoices().getFirst().id());
        SessionView end=playtests.advance(2L,10L,start.id(),1002L,1);
        assertEquals("COMPLETED",end.status());assertTrue(end.availableChoices().isEmpty());assertNotNull(end.finishedAt());
        var history=playtests.steps(2L,10L,start.id(),1,20);assertEquals(3,history.total());
        assertEquals("30",history.items().get(1).stateBefore().get("trust"));assertEquals("50",history.items().get(1).stateAfter().get("trust"));
        assertEquals(102L,history.items().get(1).nodeId());
    }
    @Test void unmetConditionIsNotVisibleOrExecutable() {
        db.update("UPDATE state_effect SET operand_value='0' WHERE id=601");
        var s=playtests.start(2L,10L);var middle=playtests.advance(2L,10L,s.id(),1001L,0);
        assertTrue(middle.deadEnd());assertTrue(middle.availableChoices().isEmpty());
        assertCode("INVALID_STATE",()->playtests.advance(2L,10L,s.id(),1002L,1));
        assertEquals(2,count("playtest_step"));
    }
    @Test void duplicateSubmissionDoesNotApplyEffectTwice() {
        var s=playtests.start(2L,10L);playtests.advance(2L,10L,s.id(),1001L,0);
        assertCode("INVALID_STATE",()->playtests.advance(2L,10L,s.id(),1001L,0));
        assertEquals("50",playtests.get(2L,10L,s.id()).state().get("trust"));assertEquals(2,count("playtest_step"));
    }
    @Test void concurrentLoopRequestsCommitExactlyOneStep() throws Exception {
        db.update("UPDATE story_choice SET enabled=1 WHERE id=1003");
        db.update("INSERT INTO state_effect(choice_id,variable_id,operation,operand_value,sort_order) VALUES(1003,501,'ADD','20',0)");
        var s=playtests.start(2L,10L);
        CountDownLatch go=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> choose=()->{go.await();try{playtests.advance(2L,10L,s.id(),1003L,0);return true;}catch(BusinessException e){assertEquals("INVALID_STATE",e.getCode());return false;}};
            Future<Boolean> a=pool.submit(choose),b=pool.submit(choose);go.countDown();
            int success=(a.get(15,TimeUnit.SECONDS)?1:0)+(b.get(15,TimeUnit.SECONDS)?1:0);
            assertEquals(1,success);
        }
        assertEquals(2,count("playtest_step"));assertEquals("50",playtests.get(2L,10L,s.id()).state().get("trust"));
    }
    @Test void rejectsForeignProjectSession() {
        var s=playtests.start(1L,10L);assertCode("NOT_FOUND",()->playtests.get(1L,20L,s.id()));
    }
    @Test void rejectsAnotherMembersSessionIncludingReplayAndRestart() {
        var s=playtests.start(2L,10L);
        assertCode("FORBIDDEN",()->playtests.get(3L,10L,s.id()));
        assertCode("FORBIDDEN",()->playtests.steps(3L,10L,s.id(),1,20));
        assertCode("FORBIDDEN",()->playtests.restart(3L,10L,s.id()));
        assertCode("FORBIDDEN",()->playtests.stop(3L,10L,s.id()));
    }
    @Test void outsidersCannotStartOrReadVariables() {
        assertCode("FORBIDDEN",()->playtests.start(4L,10L));assertCode("FORBIDDEN",()->rules.listVariables(4L,10L));
    }
    @Test void testerMayPlayButCannotEditRulesOrVariables() {
        playtests.start(2L,10L);
        assertCode("FORBIDDEN",()->rules.createVariable(2L,10L,variable("flag","BOOLEAN","true")));
        assertCode("FORBIDDEN",()->rules.replaceRules(2L,10L,101L,1001L,new RulesRequest(List.of(),List.of())));
    }
    @Test void archivedProjectAllowsReplayButNoWrites() {
        var s=playtests.start(2L,10L);db.update("UPDATE narrative_project SET status='ARCHIVED' WHERE id=10");
        assertEquals(0,playtests.get(2L,10L,s.id()).stepNo());
        assertCode("CONFLICT",()->playtests.advance(2L,10L,s.id(),1001L,0));
        assertCode("CONFLICT",()->playtests.start(2L,10L));
        assertCode("CONFLICT",()->rules.createVariable(1L,10L,variable("flag","BOOLEAN","true")));
    }
    @Test void restartPreservesOldHistoryAndUsesFreshInitialState() {
        var s=playtests.start(2L,10L);playtests.advance(2L,10L,s.id(),1001L,0);
        var fresh=playtests.restart(2L,10L,s.id());
        assertNotEquals(s.id(),fresh.id());assertEquals("30",fresh.state().get("trust"));assertEquals(0,fresh.stepNo());
        assertEquals("ABORTED",playtests.get(2L,10L,s.id()).status());assertEquals(2,playtests.steps(2L,10L,s.id(),1,20).total());
    }
    @Test void missingOrMultipleStartsDoNotCreateSession() {
        db.update("UPDATE story_node SET is_start=0 WHERE id=101");assertCode("INVALID_STATE",()->playtests.start(2L,10L));
        db.update("UPDATE story_node SET is_start=1 WHERE id IN(101,102)");assertCode("INVALID_STATE",()->playtests.start(2L,10L));
        assertEquals(0,count("playtest_session"));
    }
    @Test void startAtEndingCompletesImmediately() {
        db.update("UPDATE story_node SET is_start=0 WHERE id=101");db.update("UPDATE story_node SET is_start=1 WHERE id=103");
        var s=playtests.start(2L,10L);assertEquals("COMPLETED",s.status());assertEquals(1,count("playtest_step"));
    }
    @Test void disabledAndWrongSourceChoicesAreRejected() {
        var s=playtests.start(2L,10L);
        assertCode("INVALID_STATE",()->playtests.advance(2L,10L,s.id(),1003L,0));
        assertCode("NOT_FOUND",()->playtests.advance(2L,10L,s.id(),1002L,0));
    }
    @Test void overflowDoesNotWriteStepOrMoveSession() {
        db.update("UPDATE state_variable SET initial_value=? WHERE id=501",Long.toString(Long.MAX_VALUE));
        var s=playtests.start(2L,10L);assertCode("RULE_VALUE_INVALID",()->playtests.advance(2L,10L,s.id(),1001L,0));
        assertEquals(1,count("playtest_step"));assertEquals(101L,playtests.get(2L,10L,s.id()).currentNode().id());
    }
    @Test void rollbackAfterStepInsertRestoresBothStepAndSession() {
        // The next node has an invalid rule; response assembly fails after both writes.
        db.update("UPDATE choice_condition SET expected_value='bad' WHERE id=701");
        var s=playtests.start(2L,10L);assertCode("RULE_VALUE_INVALID",()->playtests.advance(2L,10L,s.id(),1001L,0));
        assertEquals(1,count("playtest_step"));assertEquals(101L,playtests.get(2L,10L,s.id()).currentNode().id());
    }
    @Test void failedRestartRollsBackOldSessionAbort() {
        var s=playtests.start(2L,10L);db.update("UPDATE story_node SET is_start=0 WHERE id=101");
        assertCode("INVALID_STATE",()->playtests.restart(2L,10L,s.id()));
        assertEquals("RUNNING",db.queryForObject("SELECT status FROM playtest_session WHERE id=?",String.class,s.id()));
    }
    @Test void variableCrudChecksTypesUniquenessReferencesAndActiveSessions() {
        var v=rules.createVariable(1L,10L,variable("flag","BOOLEAN","true"));
        assertEquals("false",rules.updateVariable(1L,10L,v.id(),variable("flag","BOOLEAN","false")).initialValue());
        assertCode("CONFLICT",()->rules.createVariable(1L,10L,variable("flag","BOOLEAN","true")));
        assertCode("RULE_VALUE_INVALID",()->rules.createVariable(1L,10L,variable("broken","INTEGER","abc")));
        assertCode("RESOURCE_IN_USE",()->rules.deleteVariable(1L,10L,501L));
        assertCode("RESOURCE_IN_USE",()->rules.updateVariable(1L,10L,501L,variable("trust","STRING","x")));
        assertCode("NOT_FOUND",()->rules.getVariable(1L,10L,502L));
        rules.deleteVariable(1L,10L,v.id());assertCode("NOT_FOUND",()->rules.getVariable(1L,10L,v.id()));
        var s=playtests.start(2L,10L);assertCode("CONFLICT",()->rules.createVariable(1L,10L,variable("flag","BOOLEAN","true")));
        playtests.stop(2L,10L,s.id());assertNotNull(rules.createVariable(1L,10L,variable("flag","BOOLEAN","true")).id());
    }
    @Test void variableDescriptionCanBeCleared() {
        var v=rules.createVariable(1L,10L,new VariableRequest("flag","Flag","BOOLEAN","true","old description"));
        rules.updateVariable(1L,10L,v.id(),variable("flag","BOOLEAN","false"));
        assertNull(rules.getVariable(1L,10L,v.id()).description());
    }
    @Test void crossProjectTargetIsRejectedBeforeAnyStepWrite() {
        db.update("UPDATE story_choice SET target_node_id=201 WHERE id=1001");
        var s=playtests.start(2L,10L);
        assertCode("NOT_FOUND",()->playtests.advance(2L,10L,s.id(),1001L,0));
        assertEquals(1,count("playtest_step"));
    }
    @Test void invalidRuleReplacementPreservesPreviousRules() {
        var bad=new RulesRequest(List.of(),List.of(new EffectInput(501L,"ADD","abc")));
        assertCode("RULE_VALUE_INVALID",()->rules.replaceRules(1L,10L,101L,1001L,bad));
        assertEquals("20",rules.getRules(1L,10L,101L,1001L).effects().getFirst().operandValue());
        var foreign=new RulesRequest(List.of(new ConditionInput(502L,"EQ","0",0)),List.of());
        assertCode("RULE_VALUE_INVALID",()->rules.replaceRules(1L,10L,101L,1001L,foreign));
        assertCode("NOT_FOUND",()->rules.getRules(1L,10L,102L,1001L));
    }
    @Test void completeRuleReplacementPreservesOrderAndEmptyListsClearRules() {
        var r=rules.replaceRules(1L,10L,101L,1001L,new RulesRequest(
                List.of(new ConditionInput(501L,"GTE","10",0),new ConditionInput(501L,"EQ","30",1)),
                List.of(new EffectInput(501L,"SET","10"),new EffectInput(501L,"ADD","5"))));
        assertEquals(2,r.conditions().size());assertEquals(1,r.effects().get(1).sortOrder());
        var s=playtests.start(2L,10L);assertEquals("15",playtests.advance(2L,10L,s.id(),1001L,0).state().get("trust"));
        rules.replaceRules(1L,10L,101L,1001L,new RulesRequest(List.of(),List.of()));
        assertTrue(rules.getRules(1L,10L,101L,1001L).effects().isEmpty());
    }
    @Test void paginatedHistoryAndSessionListOnlyExposeOwnData() {
        var s=playtests.start(2L,10L);playtests.advance(2L,10L,s.id(),1001L,0);playtests.start(3L,10L);
        assertEquals(1,playtests.list(2L,10L,1,20).total());assertEquals(1,playtests.steps(2L,10L,s.id(),2,1).items().getFirst().stepNo());
        assertCode("VALIDATION_ERROR",()->playtests.steps(2L,10L,s.id(),0,101));
    }
    @Test void httpAuthenticationValidationAndErrorContract() throws Exception {
        String token="Bearer "+jwt.createToken(2L,"user2");
        mvc.perform(get("/api/projects/10/playtests")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/projects/10/state-variables").header("Authorization",token).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/projects/10/playtests/1/choices/1001").header("Authorization",token).contentType("application/json").content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/projects/10/state-variables/99999").header("Authorization",token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/projects/20/state-variables").header("Authorization",token)).andExpect(status().isForbidden());
        var s=playtests.start(2L,10L);
        mvc.perform(post("/api/projects/10/playtests/"+s.id()+"/choices/1001").header("Authorization",token)
                .contentType("application/json").content("{\"expectedStepNo\":99}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        mvc.perform(post("/api/projects/10/playtests/"+s.id()+"/choices/1001").header("Authorization",token)
                .contentType("application/json").content("{\"expectedStepNo\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state.trust").value("50"));
    }
    private int count(String table) {return db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
    private VariableRequest variable(String key,String type,String value) {return new VariableRequest(key,key,type,value,null);}
    private void assertCode(String code,org.junit.jupiter.api.function.Executable action) {assertEquals(code,assertThrows(BusinessException.class,action).getCode());}
}
