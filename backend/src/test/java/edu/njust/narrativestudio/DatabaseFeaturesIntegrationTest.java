package edu.njust.narrativestudio;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import edu.njust.narrativestudio.dto.DatabaseDtos.*;
import edu.njust.narrativestudio.dto.AccountDtos.*;
import edu.njust.narrativestudio.dto.StoryGraphDtos.*;
import edu.njust.narrativestudio.entity.*;
import edu.njust.narrativestudio.exception.BusinessException;
import edu.njust.narrativestudio.service.*;
import edu.njust.narrativestudio.config.JwtService;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:features;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:week3-schema.sql,classpath:database-extension-test.sql",
    "debug=false","logging.level.root=WARN","logging.level.org.springframework=ERROR"
})
@AutoConfigureMockMvc
class DatabaseFeaturesIntegrationTest {

    @Test void tenNodeDemoImportsAndReachesBothEndings() throws Exception {
        edu.njust.narrativestudio.dto.StoryTransferDtos.Document document;
        try(var stream=getClass().getResourceAsStream("/story-transfer-demo.json")) {
            document=json.readValue(stream,edu.njust.narrativestudio.dto.StoryTransferDtos.Document.class);
        }
        var p=transfer.importStory(4L,document);
        assertEquals(10,graph.getGraph(4L,p.id()).nodes().size());
        for(int ending=0;ending<2;ending++) {
            var s=playtests.start(4L,p.id());
            for(int step=0;step<7;step++) s=playtests.advance(4L,p.id(),s.id(),s.availableChoices().getFirst().id(),s.stepNo());
            assertEquals(2,s.availableChoices().size());
            s=playtests.advance(4L,p.id(),s.id(),s.availableChoices().get(ending).id(),s.stepNo());
            assertEquals("COMPLETED",s.status());assertEquals("50",s.state().get("trust"));
        }
        assertEquals(100.0,coverage.get(4L,p.id(),null).coveragePercent());
    }

    @Test void referencedCharacterCannotBeSoftDeletedAndNullableFieldsClear() {
        var payload=new edu.njust.narrativestudio.dto.CharacterDtos.SaveRequest("Alice","summary","personality","goal","values");
        characters.update(1L,10L,11L,payload);
        characters.update(1L,10L,11L,new edu.njust.narrativestudio.dto.CharacterDtos.SaveRequest("Alice",null,null,null,null));
        assertNull(characters.get(1L,10L,11L).summary());
        assertNull(characters.get(1L,10L,11L).personality());
        details.replaceCast(1L,10L,101L,new CastRequest(List.of(11L)));
        assertThrows(BusinessException.class,()->characters.delete(1L,10L,11L));
        assertEquals("ACTIVE",characters.get(1L,10L,11L).status());
        details.replaceCast(1L,10L,101L,new CastRequest(List.of()));
        characters.delete(1L,10L,11L);
        assertThrows(BusinessException.class,()->characters.get(1L,10L,11L));
    }
    @Test void fullAnalysisIncludingPersistenceMeetsLocalBudget() {
        db.update("DELETE FROM choice_condition");db.update("DELETE FROM state_effect");db.update("DELETE FROM story_choice WHERE project_id=10");
        db.update("UPDATE story_node SET node_type='NORMAL' WHERE project_id=10");
        for(int i=0;i<497;i++) db.update("INSERT INTO story_node(project_id,node_key,title,node_type,is_start) VALUES(10,?,?,'NORMAL',0)","perf"+i,"Node "+i);
        var nodes=graph.getGraph(1L,10L).nodes();
        for(int i=0;i<500;i++) for(int offset:List.of(1,7))
            db.update("INSERT INTO story_choice(project_id,source_node_id,target_node_id,choice_text,enabled) VALUES(10,?,?,'Next',1)",
                    nodes.get(i).id(),nodes.get((i+offset)%500).id());
        long start=System.nanoTime();
        assertEquals(500,issues.analyze(1L,10L).stream().filter(x->"CYCLE".equals(x.getIssueType())).count());
        double ms=(System.nanoTime()-start)/1e6;
        System.out.printf("H2 analysis + issue persistence 500 nodes / 1000 edges: %.2f ms%n",ms);
        assertTrue(ms<2000,"Local H2 analysis exceeded 2s: "+ms);
    }
    @Test void localCrudHttpPipelineP95MeetsBudget() throws Exception {
        String bearer="Bearer "+jwt.createToken(1L,"user1");
        String body="{\"entryType\":\"SETTING\",\"title\":\"Perf\",\"content\":\"Fixture\",\"sortOrder\":0}";
        List<Double> samples=new ArrayList<>();
        for(int i=0;i<30;i++) {
            long start=System.nanoTime();
            var result=mvc.perform(post("/api/projects/10/world-entries").header("Authorization",bearer).contentType("application/json").content(body))
                    .andExpect(status().isOk()).andReturn();
            if(i>=5) samples.add((System.nanoTime()-start)/1e6);
            long id=json.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
            start=System.nanoTime();
            mvc.perform(get("/api/projects/10/world-entries/"+id).header("Authorization",bearer)).andExpect(status().isOk());
            if(i>=5) samples.add((System.nanoTime()-start)/1e6);
            start=System.nanoTime();
            mvc.perform(put("/api/projects/10/world-entries/"+id).header("Authorization",bearer).contentType("application/json").content(body)).andExpect(status().isOk());
            if(i>=5) samples.add((System.nanoTime()-start)/1e6);
            start=System.nanoTime();
            mvc.perform(delete("/api/projects/10/world-entries/"+id).header("Authorization",bearer)).andExpect(status().isOk());
            if(i>=5) samples.add((System.nanoTime()-start)/1e6);
        }
        Collections.sort(samples);double p95=samples.get(94);
        System.out.printf("H2 + MockMvc CRUD 20 warmup / 100 measured requests P95: %.2f ms%n",p95);
        assertTrue(p95<500,"Local CRUD P95 exceeded 500ms: "+p95);
    }

    @Autowired StoryTransferService transfer;
    @Autowired EndingCoverageService coverage;
    @Autowired ProjectService projects;
    @Autowired CharacterService characters;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;

    @Test void portableStoryRoundTripRemapsIdsAndPreservesExecutableRules() {
        var exported=transfer.exportStory(1L,10L);
        var imported=transfer.importStory(4L,exported);
        assertNotEquals(10L,imported.id());
        assertEquals("OWNER",imported.memberRole());
        assertEquals(exported,transfer.exportStory(4L,imported.id()));
        var g=graph.getGraph(4L,imported.id());
        var s=playtests.start(4L,imported.id());
        var first=g.choices().stream().filter(c->c.choiceText().equals("Add trust")).findFirst().orElseThrow();
        var second=g.choices().stream().filter(c->c.choiceText().equals("Hidden ending")).findFirst().orElseThrow();
        playtests.advance(4L,imported.id(),s.id(),first.id(),0);
        assertEquals("COMPLETED",playtests.advance(4L,imported.id(),s.id(),second.id(),1).status());
    }
    @Test void invalidImportRollsBackNewProjectAndAllChildren() {
        var original=transfer.exportStory(1L,10L);
        var invalid=new edu.njust.narrativestudio.dto.StoryTransferDtos.Choice("start","missing","Broken",0,true,List.of(),List.of());
        var document=new edu.njust.narrativestudio.dto.StoryTransferDtos.Document(1,"Import",null,original.nodes(),original.variables(),List.of(invalid));
        assertThrows(BusinessException.class,()->transfer.importStory(4L,document));
        assertTrue(projects.listAccessible(4L).isEmpty());
        assertEquals(4,db.queryForObject("SELECT COUNT(*) FROM story_node",Integer.class));
    }
    @Test void importRejectsUnknownVersionAndNullNestedItems() throws Exception {
        var tree=json.valueToTree(transfer.exportStory(1L,10L));
        ((com.fasterxml.jackson.databind.node.ObjectNode)tree).put("schemaVersion",3);
        String bearer="Bearer "+jwt.createToken(4L,"user4");
        mvc.perform(post("/api/projects/import").header("Authorization",bearer).contentType("application/json").content(json.writeValueAsString(tree)))
                .andExpect(status().isBadRequest());
        ((com.fasterxml.jackson.databind.node.ObjectNode)tree).put("schemaVersion",1);
        ((com.fasterxml.jackson.databind.node.ArrayNode)tree.get("nodes")).addNull();
        mvc.perform(post("/api/projects/import").header("Authorization",bearer).contentType("application/json").content(json.writeValueAsString(tree)))
                .andExpect(status().isBadRequest());
        assertTrue(projects.listAccessible(4L).isEmpty());
    }
    @Test void exportAndCoverageRequireMembership() {
        assertThrows(BusinessException.class,()->transfer.exportStory(4L,10L));
        assertThrows(BusinessException.class,()->coverage.get(4L,10L,null));
    }
    @Test void coverageCountsOnlyCurrentUserAndSelectedVersion() {
        var r=releases.publish(1L,10L);
        var s=playtests.startRelease(2L,10L,r.id());
        playtests.advance(2L,10L,s.id(),1001L,0);playtests.advance(2L,10L,s.id(),1002L,1);
        assertEquals(100.0,coverage.get(2L,10L,r.id()).coveragePercent());
        assertEquals(0,coverage.get(1L,10L,r.id()).totalSessions());
        assertEquals(0,coverage.get(2L,10L,null).totalSessions());
        db.update("UPDATE story_node SET title='Edited',node_type='NORMAL' WHERE id=103");
        var frozen=coverage.get(2L,10L,r.id());
        assertEquals("End",frozen.endings().getFirst().title());
        assertEquals(1,frozen.totalEndings());
        assertEquals(0,coverage.get(2L,10L,null).totalEndings());
        assertEquals(0.0,coverage.get(2L,10L,null).coveragePercent());
        assertThrows(BusinessException.class,()->coverage.get(1L,20L,r.id()));
    }
    @Test void coverageIncludesAllHistoryBeyondFirstPage() {
        for(int i=0;i<105;i++) db.update("INSERT INTO playtest_session(project_id,tester_id,status,current_node_id,finished_at) VALUES(10,2,'COMPLETED',103,CURRENT_TIMESTAMP)");
        var result=coverage.get(2L,10L,null);
        assertEquals(105,result.totalSessions());assertEquals(105,result.completedSessions());
        assertEquals(105,result.endings().getFirst().completions());assertEquals(100.0,result.coveragePercent());
    }
    @Test void nullableNodeAndProjectFieldsCanBeCleared() {
        graph.updateNode(1L,10L,101L,new NodeRequest("start","Start","Text","NORMAL","Scene",true,null,null));
        graph.updateNode(1L,10L,101L,new NodeRequest("start","Start",null,"NORMAL",null,true,null,null));
        assertNull(graph.getNode(1L,10L,101L).content());assertNull(graph.getNode(1L,10L,101L).scene());
        projects.update(1L,10L,new edu.njust.narrativestudio.dto.ProjectDtos.UpdateRequest("Test","Description"));
        projects.update(1L,10L,new edu.njust.narrativestudio.dto.ProjectDtos.UpdateRequest("Test",null));
        assertNull(projects.getAccessible(1L,10L).description());
    }
    @Test void optionalAiDisabledAndConsentAndRoleChecksAreExplicit() throws Exception {
        String body="{\"nodeId\":101,\"characterId\":11,\"direction\":\"问候\",\"consent\":true}";
        mvc.perform(post("/api/projects/10/ai/dialogue-candidates").header("Authorization","Bearer "+jwt.createToken(1L,"user1"))
                .contentType("application/json").content(body)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("AI_UNAVAILABLE"));
        mvc.perform(post("/api/projects/10/ai/dialogue-candidates").header("Authorization","Bearer "+jwt.createToken(2L,"user2"))
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/projects/10/ai/dialogue-candidates").header("Authorization","Bearer "+jwt.createToken(1L,"user1"))
                .contentType("application/json").content(body.replace("true","false"))).andExpect(status().isBadRequest());
        assertEquals(3,graph.getGraph(1L,10L).nodes().size());
    }
    @Test void graphRejectsCapacityOverflowWithoutPartialWrites() {
        for(int i=0;i<497;i++) db.update("INSERT INTO story_node(project_id,node_key,title,node_type,is_start) VALUES(10,?,?,'NORMAL',0)","n"+i,"Node "+i);
        assertThrows(BusinessException.class,()->graph.createNode(1L,10L,new NodeRequest("overflow","Overflow",null,"NORMAL",null,false,null,null)));
        assertEquals(500,graph.getGraph(1L,10L).nodes().size());
    }
    @Test void registerRejectsPasswordsOverBcryptByteLimit() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"username\":\"newuser\",\"displayName\":\"New\",\"password\":\""+ "密".repeat(25)+"\"}"))
                .andExpect(status().isBadRequest());
    }
    @Autowired CharacterDetailService details;@Autowired ChoiceDraftService drafts;@Autowired FeedbackService feedback;
    @Autowired ReleaseService releases;@Autowired PlaytestService playtests;@Autowired IssueService issues;
    @Autowired StoryGraphService graph;@Autowired AccountService accounts;@Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;@Autowired JwtService jwt;@Autowired PasswordEncoder passwords;
    @MockitoBean AccountMailSender mail;
    AtomicReference<String> sent=new AtomicReference<>();
    @BeforeEach void fixture() {
        reset(mail);sent.set(null);
        doAnswer(inv->{sent.set(inv.getArgument(2));return null;}).when(mail).send(anyString(),anyString(),anyString());
        for(String table:List.of("account_action_token","test_feedback","playtest_step","playtest_session","story_release","story_choice_draft",
            "state_effect","choice_condition","story_choice","node_character","character_knowledge","character_relation","character_profile",
            "world_entry","detected_issue","story_node","state_variable","project_member","narrative_project","sys_user")) db.update("DELETE FROM "+table);
        String hash=passwords.encode("password123");
        for(int id=1;id<=4;id++) db.update("INSERT INTO sys_user(id,username,password_hash,display_name) VALUES(?,?,?,?)",id,"user"+id,hash,"User "+id);
        db.update("INSERT INTO narrative_project(id,name,owner_id) VALUES(10,'Test',1),(20,'Other',1)");
        db.update("INSERT INTO project_member(id,project_id,user_id,member_role) VALUES(1,10,1,'OWNER'),(2,10,2,'TESTER'),(3,10,3,'EDITOR'),(4,20,1,'OWNER')");
        db.update("INSERT INTO character_profile(id,project_id,name) VALUES(11,10,'Alice'),(12,10,'Bob'),(21,20,'Other')");
        db.update("INSERT INTO story_node(id,project_id,node_key,title,node_type,is_start) VALUES(101,10,'start','Start','NORMAL',1),(102,10,'middle','Middle','NORMAL',0),(103,10,'end','End','ENDING',0),(201,20,'foreign','Other','NORMAL',1)");
        db.update("INSERT INTO story_choice(id,project_id,source_node_id,target_node_id,choice_text,enabled) VALUES(1001,10,101,102,'Add trust',1),(1002,10,102,103,'Hidden ending',1),(1003,10,101,101,'Loop',0)");
        db.update("INSERT INTO state_variable(id,project_id,variable_key,display_name,value_type,initial_value) VALUES(501,10,'trust','Trust','INTEGER','30')");
        db.update("INSERT INTO state_effect(id,choice_id,variable_id,operation,operand_value,sort_order) VALUES(601,1001,501,'ADD','20',0)");
        db.update("INSERT INTO choice_condition(id,choice_id,variable_id,operator,expected_value,condition_group,sort_order) VALUES(701,1002,501,'GTE','50',0,0)");
    }
    @Test void concurrentPublicationAllocatesDifferentVersions() throws Exception {
        try(var executor=Executors.newFixedThreadPool(2)) {
            CountDownLatch start=new CountDownLatch(1);
            Callable<Integer> action=()->{start.await();return releases.publish(1L,10L).versionNo();};
            var a=executor.submit(action);var b=executor.submit(action);start.countDown();
            assertEquals(Set.of(1,2),Set.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)));
        }
    }
    @Test void concurrentDraftPromotionCreatesOnlyOneChoice() throws Exception {
        var d=drafts.save(1L,10L,null,new DraftRequest(101L,"new",0));
        try(var executor=Executors.newFixedThreadPool(2)) {
            CountDownLatch start=new CountDownLatch(1);
            Callable<Boolean> action=()->{start.await();try {drafts.promote(1L,10L,d.getId(),new PromoteRequest(103L));return true;}catch(BusinessException ex){return false;}};
            var a=executor.submit(action);var b=executor.submit(action);start.countDown();
            assertEquals(1,(a.get(15,TimeUnit.SECONDS)?1:0)+(b.get(15,TimeUnit.SECONDS)?1:0));
        }
        assertEquals(4,db.queryForObject("SELECT COUNT(*) FROM story_choice WHERE project_id=10",Integer.class));
    }
    @Test void resetTransportFailureIsNeutralAndRollsBack() {
        db.update("UPDATE sys_user SET email='owner@example.com',email_verified_at=CURRENT_TIMESTAMP WHERE id=1");
        doThrow(new BusinessException("MAIL_UNAVAILABLE","Unavailable",org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE))
            .when(mail).send(anyString(),anyString(),anyString());
        assertDoesNotThrow(()->accounts.requestReset(new ResetRequest("owner@example.com")));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM account_action_token",Integer.class));
    }
    @Test void missingMailConfigurationIsExplicitAndWritesNothing() {
        doThrow(new BusinessException("MAIL_UNAVAILABLE","Unavailable",org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)).when(mail).requireConfigured();
        assertThrows(BusinessException.class,()->accounts.requestReset(new ResetRequest("missing@example.com")));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM account_action_token",Integer.class));
    }
    @Test void httpRoutesExposeAllProjectExtensionModules() throws Exception {
        String bearer="Bearer "+jwt.createToken(1L,"user1");
        mvc.perform(post("/api/projects/10/character-relations").header("Authorization",bearer).contentType("application/json")
            .content("{\"sourceCharacterId\":11,\"targetCharacterId\":12,\"relationType\":\"TRUST\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").isNumber());
        mvc.perform(post("/api/projects/10/characters/11/knowledge").header("Authorization",bearer).contentType("application/json")
            .content("{\"knowledgeKey\":\"secret\",\"knowledgeLevel\":\"KNOWN\",\"acquiredNodeId\":102}")).andExpect(status().isOk());
        mvc.perform(put("/api/projects/10/story-nodes/101/characters").header("Authorization",bearer).contentType("application/json")
            .content("{\"characterIds\":[11,12]}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(post("/api/projects/10/choice-drafts").header("Authorization",bearer).contentType("application/json")
            .content("{\"sourceNodeId\":101,\"sortOrder\":0}")).andExpect(status().isOk());
        mvc.perform(post("/api/projects/10/feedback").header("Authorization",bearer).contentType("application/json")
            .content("{\"title\":\"Bug\",\"description\":\"Description\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/projects/10/analysis-runs").header("Authorization",bearer)).andExpect(status().isOk());
        mvc.perform(post("/api/projects/10/releases").header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.data.versionNo").value(1));
        Long release=db.queryForObject("SELECT id FROM story_release WHERE project_id=10",Long.class);
        mvc.perform(post("/api/projects/10/releases/"+release+"/playtests").header("Authorization",bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.releaseId").value(release));
        for(String path:List.of("character-relations","characters/11/knowledge","story-nodes/101/characters","choice-drafts","feedback","issues","releases"))
            mvc.perform(get("/api/projects/10/"+path).header("Authorization",bearer)).andExpect(status().isOk());
    }
    @Test void changedRulesDoNotRecomputePriorKnowledgeOrState() {
        var r=releases.publish(1L,10L);var s=playtests.startRelease(2L,10L,r.id());
        playtests.advance(2L,10L,s.id(),1001L,0);
        db.update("DELETE FROM state_effect WHERE choice_id=1001");
        assertEquals("50",playtests.steps(2L,10L,s.id(),1,20).items().getLast().stateAfter().get("trust"));
    }
    @Test void relationCrudAndUniqueness() {
        var r=details.saveRelation(1L,10L,null,new RelationRequest(11L,12L,"TRUST","old"));
        assertEquals(1,details.relations(2L,10L).size());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->details.saveRelation(1L,10L,null,new RelationRequest(11L,12L,"TRUST","duplicate")));
        details.saveRelation(1L,10L,r.getId(),new RelationRequest(11L,12L,"RIVAL","new"));
        assertEquals("RIVAL",details.relation(1L,10L,r.getId()).getRelationType());
        details.deleteRelation(1L,10L,r.getId());assertTrue(details.relations(1L,10L).isEmpty());
    }
    @Test void rejectsSelfAndCrossProjectRelations() {
        assertThrows(BusinessException.class,()->details.saveRelation(1L,10L,null,new RelationRequest(11L,11L,"TRUST",null)));
        assertThrows(BusinessException.class,()->details.saveRelation(1L,10L,null,new RelationRequest(11L,21L,"TRUST",null)));
    }
    @Test void knowledgeCrudCanClearNullableFields() {
        var k=details.saveKnowledge(1L,10L,11L,null,new KnowledgeRequest("secret","KNOWN","old",102L));
        details.saveKnowledge(1L,10L,11L,k.getId(),new KnowledgeRequest("secret","SUSPECTED",null,null));
        var actual=details.knowledgeItem(2L,10L,11L,k.getId());assertNull(actual.getAcquiredNodeId());assertNull(actual.getDescription());
        details.deleteKnowledge(1L,10L,11L,k.getId());assertTrue(details.knowledge(1L,10L,11L).isEmpty());
    }
    @Test void knowledgeRejectsForeignNodeAndWrongCharacter() {
        assertThrows(BusinessException.class,()->details.saveKnowledge(1L,10L,11L,null,new KnowledgeRequest("secret","KNOWN",null,201L)));
        var k=details.saveKnowledge(1L,10L,11L,null,new KnowledgeRequest("secret","KNOWN",null,null));
        assertThrows(BusinessException.class,()->details.knowledgeItem(1L,10L,12L,k.getId()));
    }
    @Test void castReplacementValidatesWholeBatchBeforeDelete() {
        details.replaceCast(1L,10L,101L,new CastRequest(List.of(11L)));
        assertThrows(BusinessException.class,()->details.replaceCast(1L,10L,101L,new CastRequest(List.of(12L,21L))));
        assertEquals(List.of(11L),details.cast(2L,10L,101L));
        assertThrows(BusinessException.class,()->details.replaceCast(1L,10L,101L,new CastRequest(List.of(11L,11L))));
        assertTrue(details.replaceCast(1L,10L,101L,new CastRequest(List.of())).isEmpty());
    }
    @Test void draftPromotionIsAtomicAndSingleUse() {
        var d=drafts.save(1L,10L,null,new DraftRequest(101L,"new branch",1));
        assertThrows(BusinessException.class,()->drafts.promote(1L,10L,d.getId(),new PromoteRequest(201L)));
        assertNotNull(drafts.get(1L,10L,d.getId()));
        var c=drafts.promote(1L,10L,d.getId(),new PromoteRequest(103L));assertEquals(103L,c.targetNodeId());
        assertThrows(BusinessException.class,()->drafts.promote(1L,10L,d.getId(),new PromoteRequest(103L)));
    }
    @Test void emptyDraftCanBeSavedButNotPromoted() {
        var d=drafts.save(1L,10L,null,new DraftRequest(101L,null,0));
        assertThrows(BusinessException.class,()->drafts.promote(1L,10L,d.getId(),new PromoteRequest(103L)));
        assertThrows(BusinessException.class,()->drafts.save(1L,10L,null,new DraftRequest(103L,"ending",0)));
        drafts.delete(1L,10L,d.getId());assertEquals(0,drafts.list(1L,10L,1,20).total());
    }
    @Test void feedbackAssociatesOwnStepAndEditorsCanResolve() {
        var s=playtests.start(2L,10L);Long step=playtests.steps(2L,10L,s.id(),1,20).items().getFirst().id();
        var f=feedback.create(2L,10L,new FeedbackRequest(s.id(),step,"Bug","Description"));
        assertEquals(step,f.getStepId());assertEquals(1,feedback.list(1L,10L,1,20).total());
        assertEquals("RESOLVED",feedback.status(3L,10L,f.getId(),new StatusRequest("RESOLVED")).getStatus());
        assertThrows(BusinessException.class,()->feedback.status(2L,10L,f.getId(),new StatusRequest("OPEN")));
    }
    @Test void feedbackRejectsOtherUsersSessionAndMismatchedStep() {
        var a=playtests.start(1L,10L);var b=playtests.start(2L,10L);
        Long foreignStep=playtests.steps(1L,10L,a.id(),1,20).items().getFirst().id();
        assertThrows(BusinessException.class,()->feedback.create(2L,10L,new FeedbackRequest(a.id(),null,"Bug","text")));
        assertThrows(BusinessException.class,()->feedback.create(2L,10L,new FeedbackRequest(b.id(),foreignStep,"Bug","text")));
        assertThrows(BusinessException.class,()->feedback.create(2L,10L,new FeedbackRequest(null,foreignStep,"Bug","text")));
        assertEquals(0,feedback.list(1L,10L,1,20).total());
    }
    @Test void feedbackTesterCannotReadOthers() {
        var f=feedback.create(1L,10L,new FeedbackRequest(null,null,"Private","text"));
        assertEquals(0,feedback.list(2L,10L,1,20).total());
        assertThrows(BusinessException.class,()->feedback.get(2L,10L,f.getId()));
    }
    @Test void releaseFreezesContentRulesAndDisabledChoices() {
        var r=releases.publish(1L,10L);
        db.update("UPDATE story_node SET title='Changed',content='new content' WHERE id=102");
        db.update("UPDATE state_effect SET operand_value='100' WHERE id=601");
        db.update("UPDATE story_choice SET enabled=0 WHERE id=1001");
        var s=playtests.startRelease(2L,10L,r.id());
        var middle=playtests.advance(2L,10L,s.id(),1001L,0);
        assertEquals("Middle",middle.currentNode().title());assertEquals("50",middle.state().get("trust"));
        assertEquals(r.id(),middle.releaseId());
        assertEquals("COMPLETED",playtests.advance(2L,10L,s.id(),1002L,1).status());
        assertEquals(r.id(),playtests.restart(2L,10L,s.id()).releaseId());
    }
    @Test void releaseListsVersionsAndIncludesAuthoringData() {
        details.replaceCast(1L,10L,101L,new CastRequest(List.of(11L)));
        details.saveRelation(1L,10L,null,new RelationRequest(11L,12L,"TRUST",null));
        var a=releases.publish(1L,10L);var b=releases.publish(1L,10L);
        assertEquals(1,a.versionNo());assertEquals(2,b.versionNo());
        var frozen=releases.get(2L,10L,a.id());assertEquals(List.of(11L),frozen.cast().get(101L));assertEquals(1,frozen.relations().size());
        assertEquals(2,releases.list(2L,10L,1,20).total());
    }
    @Test void releaseOnlyOwnerAndCannotCrossProject() {
        assertThrows(BusinessException.class,()->releases.publish(3L,10L));
        var r=releases.publish(1L,10L);
        assertThrows(BusinessException.class,()->playtests.startRelease(1L,20L,r.id()));
        assertThrows(BusinessException.class,()->releases.get(4L,10L,r.id()));
    }
    @Test void publishedIdsCannotBePhysicallyDeleted() {
        releases.publish(1L,10L);
        assertThrows(BusinessException.class,()->graph.deleteChoice(1L,10L,101L,1001L));
        assertThrows(BusinessException.class,()->graph.deleteNode(1L,10L,101L));
        assertEquals(3,graph.getGraph(1L,10L).nodes().size());
    }
    @Test void invalidReleaseRollsBackWithoutVersionRow() {
        db.update("UPDATE story_node SET is_start=0 WHERE project_id=10");
        assertThrows(BusinessException.class,()->releases.publish(1L,10L));assertEquals(0,releases.list(1L,10L,1,20).total());
    }
    @Test void knowledgeSnapshotsAreSessionLocalAndFrozen() {
        details.saveKnowledge(1L,10L,11L,null,new KnowledgeRequest("initial","SUSPECTED",null,null));
        var k=details.saveKnowledge(1L,10L,11L,null,new KnowledgeRequest("secret","KNOWN",null,102L));
        var r=releases.publish(1L,10L);
        details.saveKnowledge(1L,10L,11L,k.getId(),new KnowledgeRequest("secret","UNKNOWN",null,102L));
        var a=playtests.startRelease(2L,10L,r.id());var b=playtests.startRelease(1L,10L,r.id());
        assertEquals(Map.of("11:initial","SUSPECTED"),a.knowledge());
        var next=playtests.advance(2L,10L,a.id(),1001L,0);assertEquals("KNOWN",next.knowledge().get("11:secret"));
        assertFalse(playtests.get(1L,10L,b.id()).knowledge().containsKey("11:secret"));
        var step=playtests.steps(2L,10L,a.id(),1,20).items().getLast();
        assertFalse(step.knowledgeBefore().containsKey("11:secret"));assertEquals("KNOWN",step.knowledgeAfter().get("11:secret"));
    }
    @Test void liveKnowledgeAcquiresAtNodeWithoutMutatingDefinitions() {
        var k=details.saveKnowledge(1L,10L,11L,null,new KnowledgeRequest("secret","KNOWN",null,102L));
        var s=playtests.start(2L,10L);assertTrue(s.knowledge().isEmpty());
        assertEquals("KNOWN",playtests.advance(2L,10L,s.id(),1001L,0).knowledge().get("11:secret"));
        assertEquals("KNOWN",details.knowledgeItem(1L,10L,11L,k.getId()).getKnowledgeLevel());
    }
    @Test void structuralAnalysisDetectsCycleAndResolvesFixedIssues() {
        db.update("UPDATE story_choice SET enabled=1 WHERE id=1003");
        var first=issues.analyze(1L,10L);
        var cycle=first.stream().filter(i->"CYCLE".equals(i.getIssueType())).findFirst().orElseThrow();
        assertEquals(1,first.stream().filter(i->"CYCLE".equals(i.getIssueType())).count());
        issues.analyze(1L,10L);assertEquals(first.size(),issues.list(2L,10L,1,100).total());
        db.update("UPDATE story_choice SET enabled=0 WHERE id=1003");
        issues.analyze(1L,10L);assertEquals("RESOLVED",issues.get(2L,10L,cycle.getId()).getStatus());
    }
    @Test void analysisDetectsIsolatedUnreachableAndDeadEnd() {
        db.update("INSERT INTO story_node(id,project_id,node_key,title) VALUES(104,10,'orphan','Orphan')");
        var found=issues.analyze(1L,10L).stream().filter(i->Long.valueOf(104).equals(i.getTargetId())).map(DetectedIssue::getIssueType).toList();
        assertTrue(found.containsAll(List.of("ISOLATED","UNREACHABLE","DEAD_END")));
    }
    @Test void analysisRetainsIgnoredStatusAndReopensPersistingResolvedIssue() {
        db.update("UPDATE story_choice SET enabled=1 WHERE id=1003");
        var i=issues.analyze(1L,10L).getFirst();issues.status(1L,10L,i.getId(),new StatusRequest("IGNORED"));
        issues.analyze(1L,10L);assertEquals("IGNORED",issues.get(1L,10L,i.getId()).getStatus());
        issues.status(1L,10L,i.getId(),new StatusRequest("RESOLVED"));
        issues.analyze(1L,10L);assertEquals("OPEN",issues.get(1L,10L,i.getId()).getStatus());assertNull(issues.get(1L,10L,i.getId()).getResolvedAt());
    }
    @Test void testerAndArchivedWritesRejected() {
        assertThrows(BusinessException.class,()->details.saveRelation(2L,10L,null,new RelationRequest(11L,12L,"TRUST",null)));
        assertThrows(BusinessException.class,()->issues.analyze(2L,10L));
        db.update("UPDATE narrative_project SET status='ARCHIVED' WHERE id=10");
        assertThrows(BusinessException.class,()->drafts.save(1L,10L,null,new DraftRequest(101L,"x",0)));
        assertThrows(BusinessException.class,()->feedback.create(2L,10L,new FeedbackRequest(null,null,"x","x")));
        assertThrows(BusinessException.class,()->releases.publish(1L,10L));
    }
    @Test void paginationAndOutsiderChecks() {
        assertThrows(BusinessException.class,()->drafts.list(1L,10L,0,20));
        assertThrows(BusinessException.class,()->issues.list(1L,10L,1,101));
        assertThrows(BusinessException.class,()->details.relations(4L,10L));
        assertThrows(BusinessException.class,()->details.cast(1L,10L,201L));
    }
    @Test void emailVerificationConsumesHashedTokenAndRevokesJwt() throws Exception {
        String old=jwt.createToken(1L,"user1");
        accounts.requestEmail(1L,new EmailRequest("Owner@Example.com","password123"));String raw=sent.get();assertNotNull(raw);
        byte[] digest=db.queryForObject("SELECT token_hash FROM account_action_token",byte[].class);assertEquals(32,digest.length);
        accounts.confirmEmail(new TokenRequest(raw));assertEquals("owner@example.com",accounts.profile(1L).email());
        assertThrows(BusinessException.class,()->accounts.confirmEmail(new TokenRequest(raw)));
        mvc.perform(get("/api/account").header("Authorization","Bearer "+old)).andExpect(status().isUnauthorized());
    }
    @Test void passwordResetRequiresVerifiedEmailAndInvalidatesAllTokens() throws Exception {
        db.update("UPDATE sys_user SET email='owner@example.com',email_verified_at=CURRENT_TIMESTAMP WHERE id=1");
        accounts.requestReset(new ResetRequest("owner@example.com"));String raw=sent.get();assertNotNull(raw);
        String old=jwt.createToken(1L,"user1");
        accounts.reset(new ResetConfirm(raw,"newPassword123"));
        assertTrue(passwords.matches("newPassword123",db.queryForObject("SELECT password_hash FROM sys_user WHERE id=1",String.class)));
        assertThrows(BusinessException.class,()->accounts.reset(new ResetConfirm(raw,"anotherPass123")));
        mvc.perform(get("/api/account").header("Authorization","Bearer "+old)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"username\":\"user1\",\"password\":\"newPassword123\"}")).andExpect(status().isOk());
    }
    @Test void unknownOrUnverifiedResetDoesNotSendOrRevealAccount() {
        accounts.requestReset(new ResetRequest("missing@example.com"));verify(mail,never()).send(anyString(),anyString(),anyString());
        db.update("UPDATE sys_user SET email='owner@example.com' WHERE id=1");
        accounts.requestReset(new ResetRequest("owner@example.com"));verify(mail,never()).send(anyString(),anyString(),anyString());
    }
    @Test void expiredAndWrongPurposeTokensRejected() {
        accounts.requestEmail(1L,new EmailRequest("owner@example.com","password123"));String raw=sent.get();
        assertThrows(BusinessException.class,()->accounts.reset(new ResetConfirm(raw,"newPassword123")));
        db.update("UPDATE account_action_token SET expires_at=DATEADD('MINUTE',-1,CURRENT_TIMESTAMP)");
        assertThrows(BusinessException.class,()->accounts.confirmEmail(new TokenRequest(raw)));
        assertNull(accounts.profile(1L).email());
    }
    @Test void requestRateLimitAndWrongPassword() {
        assertThrows(BusinessException.class,()->accounts.requestEmail(1L,new EmailRequest("owner@example.com","wrong")));
        accounts.requestEmail(1L,new EmailRequest("owner@example.com","password123"));
        accounts.requestEmail(1L,new EmailRequest("owner@example.com","password123"));
        verify(mail,times(1)).send(anyString(),anyString(),anyString());
    }
    @Test void mailFailureRollsBackCredentialAndEmailOwnershipConflictDoesNotConsume() {
        doThrow(new BusinessException("MAIL_UNAVAILABLE","Unavailable",org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE))
            .when(mail).send(anyString(),anyString(),anyString());
        assertThrows(BusinessException.class,()->accounts.requestEmail(1L,new EmailRequest("owner@example.com","password123")));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM account_action_token",Integer.class));
    }
    @Test void duplicateEmailConfirmationRollsBackTokenUse() {
        db.update("UPDATE sys_user SET email='taken@example.com',email_verified_at=CURRENT_TIMESTAMP WHERE id=2");
        accounts.requestEmail(1L,new EmailRequest("taken@example.com","password123"));String raw=sent.get();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->accounts.confirmEmail(new TokenRequest(raw)));
        assertNull(db.queryForObject("SELECT used_at FROM account_action_token",java.sql.Timestamp.class));
    }
    @Test void passwordChangeAndAdminDisableRevokeTokens() throws Exception {
        accounts.changePassword(1L,new PasswordRequest("password123","newPassword123"));
        assertThrows(BusinessException.class,()->accounts.changePassword(1L,new PasswordRequest("password123","another123")));
        assertThrows(BusinessException.class,()->accounts.status(1L,2L,new UserStatus("DISABLED")));
        db.update("UPDATE sys_user SET role='ADMIN' WHERE id=1");
        assertEquals(4,accounts.users(1L,1,20).total());
        accounts.status(1L,2L,new UserStatus("DISABLED"));
        mvc.perform(get("/api/account").header("Authorization","Bearer "+jwt.createToken(2L,"user2"))).andExpect(status().isUnauthorized());
        assertThrows(BusinessException.class,()->accounts.status(1L,1L,new UserStatus("DISABLED")));
    }
    @Test void passwordChangeRejectsWrongCurrentPasswordWithoutChangingCredential() throws Exception {
        String bearer="Bearer "+jwt.createToken(1L,"user1");
        mvc.perform(put("/api/account/password").header("Authorization",bearer).contentType("application/json")
                .content("{\"currentPassword\":\"wrong-password\",\"newPassword\":\"newPassword123\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.error.message").value("当前密码错误"));
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"user1\",\"password\":\"password123\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"user1\",\"password\":\"newPassword123\"}"))
            .andExpect(status().isUnauthorized());
    }
    @Test void concurrentTokenConsumptionSucceedsOnce() throws Exception {
        accounts.requestEmail(1L,new EmailRequest("owner@example.com","password123"));String raw=sent.get();
        try(var executor=Executors.newFixedThreadPool(2)) {
            CountDownLatch start=new CountDownLatch(1);
            Callable<Boolean> action=()->{start.await();try {accounts.confirmEmail(new TokenRequest(raw));return true;}catch(BusinessException ex){return false;}};
            var a=executor.submit(action);var b=executor.submit(action);start.countDown();
            assertEquals(1,(a.get(15,TimeUnit.SECONDS)?1:0)+(b.get(15,TimeUnit.SECONDS)?1:0));
        }
    }
    @Test void httpValidationAuthenticationAndSafeProfile() throws Exception {
        mvc.perform(get("/api/projects/10/character-relations")).andExpect(status().isUnauthorized());
        String bearer="Bearer "+jwt.createToken(1L,"user1");
        mvc.perform(post("/api/projects/10/character-relations").header("Authorization",bearer).contentType("application/json")
            .content("{\"sourceCharacterId\":11,\"targetCharacterId\":12,\"relationType\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/account").header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        mvc.perform(post("/api/auth/password-resets/confirm").contentType("application/json").content("{\"token\":\"bad\",\"password\":\"short\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/projects/10/issues/1/status").header("Authorization",bearer).contentType("application/json").content("{\"status\":\"INVALID\"}"))
            .andExpect(status().isBadRequest());
    }
}
