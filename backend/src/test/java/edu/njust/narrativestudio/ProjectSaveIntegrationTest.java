package edu.njust.narrativestudio;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.njust.narrativestudio.config.JwtService;
import edu.njust.narrativestudio.dto.ProjectSaveDtos.*;
import edu.njust.narrativestudio.service.*;
import edu.njust.narrativestudio.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:saves;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:week3-schema.sql,classpath:database-extension-test.sql",
 "debug=false","logging.level.root=WARN","logging.level.org.springframework=ERROR"
})
@AutoConfigureMockMvc
class ProjectSaveIntegrationTest {
 @Autowired ProjectSaveService saves;
 @Autowired AuthoringSnapshotStore snapshots;
 @Autowired ReleaseService releases;
 @Autowired PlaytestService play;
 @Autowired JdbcTemplate db;
 @Autowired ObjectMapper json;
 @Autowired MockMvc mvc;
 @Autowired JwtService jwt;
 @BeforeEach void seed() {
  for(String t:List.of("project_save","player_progress","account_action_token","test_feedback","playtest_step","playtest_session","story_release",
    "story_choice_draft","state_effect","choice_condition","story_choice","node_character","character_knowledge","character_relation",
    "character_profile","world_entry","detected_issue","story_node","state_variable","project_member","narrative_project","sys_user"))db.update("DELETE FROM "+t);
  db.update("INSERT INTO sys_user(id,username,password_hash,display_name) VALUES(1,'owner','test','Owner'),(2,'editor','test','Editor'),(3,'tester','test','Tester'),(4,'outsider','test','Other')");
  db.update("INSERT INTO narrative_project(id,name,description,owner_id) VALUES(10,'Original','Description',1),(20,'Other',1,1)");
  db.update("INSERT INTO project_member(project_id,user_id,member_role) VALUES(10,1,'OWNER'),(10,2,'EDITOR'),(10,3,'TESTER'),(20,1,'OWNER')");
  db.update("INSERT INTO story_node(id,project_id,node_key,title,node_type,is_start) VALUES(100,10,'start','Start','NORMAL',1),(101,10,'end','Ending','ENDING',0)");
  db.update("INSERT INTO story_choice(id,project_id,source_node_id,target_node_id,choice_text,enabled) VALUES(200,10,100,101,'Go',1)");
  db.update("INSERT INTO state_variable(id,project_id,variable_key,display_name,value_type,initial_value,persistence_scope) VALUES(300,10,'flag','Flag','BOOLEAN','false','PROFILE')");
  db.update("INSERT INTO choice_condition(id,choice_id,variable_id,operator,expected_value) VALUES(400,200,300,'EQ','false')");
  db.update("INSERT INTO state_effect(id,choice_id,variable_id,operation,operand_value) VALUES(401,200,300,'SET','true')");
  db.update("INSERT INTO world_entry(id,project_id,entry_type,title,content) VALUES(500,10,'SETTING','World','Lore')");
  db.update("INSERT INTO character_profile(id,project_id,name,status) VALUES(600,10,'A','ACTIVE'),(601,10,'B','ARCHIVED')");
  db.update("INSERT INTO character_relation(id,project_id,source_character_id,target_character_id,relation_type) VALUES(602,10,600,601,'TRUST')");
  db.update("INSERT INTO character_knowledge(id,project_id,character_id,knowledge_key,knowledge_level,acquired_node_id) VALUES(603,10,600,'secret','KNOWN',100)");
  db.update("INSERT INTO node_character(node_id,character_id) VALUES(100,600)");
  db.update("INSERT INTO story_choice_draft(id,project_id,source_node_id,choice_text,created_by) VALUES(700,10,100,'Unfinished',1)");
 }
 SaveRequest request(String label,long revision,String baseline) {return new SaveRequest(label,json.createObjectNode(),revision,baseline);}
 Summary manual(String label) {return saves.manual(1L,10L,request(label,0,null)).saved();}
 @Test void onlyManualButtonCountsAndRetainsNewestFiveAcrossAuthors() {
  var first=manual("1");
  for(int i=2;i<=7;i++)saves.manual(i%2==0?2L:1L,10L,request(""+i,0,null));
  var state=saves.state(1L,10L);
  assertEquals(List.of("7","6","5","4","3"),state.manual().stream().map(Summary::label).toList());
  assertThrows(BusinessException.class,()->saves.detail(1L,10L,first.id()));
  assertEquals(5,db.queryForObject("SELECT COUNT(*) FROM project_save",Integer.class));
 }
 @Test void unchangedAutoSkipsAndChangedAutoOverwritesOneSlotWithoutTouchingManual() {
  for(int i=0;i<5;i++)manual(""+i);
  String baseline=saves.state(1L,10L).currentHash();
  assertFalse(saves.automatic(1L,10L,request(null,0,baseline)).changed());
  assertNull(saves.state(1L,10L).automatic());
  db.update("UPDATE story_node SET content='changed' WHERE id=100");
  var first=saves.automatic(1L,10L,request(null,0,baseline)).saved();
  var repeated=saves.automatic(1L,10L,request(null,first.revision(),baseline));
  assertFalse(repeated.changed());assertEquals(first.savedAt(),repeated.saved().savedAt());
  db.update("UPDATE story_node SET content='changed again' WHERE id=100");
  var second=saves.automatic(1L,10L,request(null,first.revision(),baseline)).saved();
  assertEquals(first.id(),second.id());assertEquals(2,second.revision());assertEquals(5,saves.state(1L,10L).manual().size());
 }
 @Test void timestampsAndObjectKeyOrderingDoNotCreateAutoVersionsButDraftEditsDo() throws Exception {
  String baseline=saves.state(1L,10L).currentHash();
  db.update("UPDATE story_node SET updated_at=CURRENT_TIMESTAMP WHERE id=100");
  assertFalse(saves.automatic(1L,10L,request(null,0,baseline)).changed());
  var a=json.readTree("{\"node\":{\"values\":{\"title\":\"unsaved\",\"content\":\"abc\"},\"module\":\"story\"}}");
  var first=saves.automatic(1L,10L,new SaveRequest(null,a,0L,baseline)).saved();
  var b=json.readTree("{\"node\":{\"module\":\"story\",\"values\":{\"content\":\"abc\",\"title\":\"unsaved\"}}}");
  assertFalse(saves.automatic(1L,10L,new SaveRequest(null,b,first.revision(),baseline)).changed());
 }
 @Test void autoIsPrivateAndProjectScopedAndStaleTabsCannotOverwrite() {
  String baseline=saves.state(1L,10L).currentHash();
  db.update("UPDATE story_node SET content='changed' WHERE id=100");
  var first=saves.automatic(1L,10L,request(null,0,baseline)).saved();
  assertNull(saves.state(2L,10L).automatic());assertNull(saves.state(1L,20L).automatic());
  assertThrows(BusinessException.class,()->saves.detail(2L,10L,first.id()));
  assertThrows(BusinessException.class,()->saves.detail(1L,20L,first.id()));
  assertThrows(BusinessException.class,()->saves.automatic(1L,10L,request(null,0,baseline)));
 }
 @Test void restoreCopiesAllAuthoredTablesAndRemapsIdsWithoutDestroyingReleaseOrPlaytests() throws Exception {
  db.update("UPDATE story_choice SET unlock_rule=? WHERE id=200","{\"type\":\"VISITED\",\"nodeKey\":\"start\"}");
  var draft=json.readTree("{\"choice\":{\"module\":\"story\",\"label\":\"Choice\",\"entityTable\":\"story_choice\",\"entityId\":200,\"values\":{\"sourceNodeId\":100,\"targetNodeId\":101,\"choiceText\":\"unsaved\"}}}");
  var saved=saves.manual(1L,10L,new SaveRequest("Before",draft,0L,null)).saved();
  var release=releases.publish(1L,10L);var session=play.start(1L,10L);
  db.update("UPDATE story_node SET title='Wrong change' WHERE id=100");
  var copy=saves.restore(1L,10L,saved.id(),new RestoreRequest(true,saved.revision(),"Recovered"));
  long p=copy.project().id();
  assertNotEquals(10L,p);assertEquals("Wrong change",db.queryForObject("SELECT title FROM story_node WHERE id=100",String.class));
  assertEquals("Start",db.queryForObject("SELECT title FROM story_node WHERE project_id=? AND node_key='start'",String.class,p));
  assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM character_profile WHERE project_id=?",Integer.class,p));
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM character_knowledge WHERE project_id=?",Integer.class,p));
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM character_relation WHERE project_id=?",Integer.class,p));
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM story_choice_draft WHERE project_id=?",Integer.class,p));
  long choice=db.queryForObject("SELECT id FROM story_choice WHERE project_id=?",Long.class,p);
  long variable=db.queryForObject("SELECT id FROM state_variable WHERE project_id=?",Long.class,p);
  assertEquals(variable,db.queryForObject("SELECT variable_id FROM choice_condition WHERE choice_id=?",Long.class,choice));
  assertEquals(choice,copy.drafts().path("choice").path("entityId").asLong());
  assertNotEquals(100,copy.drafts().path("choice").path("values").path("sourceNodeId").asLong());
  assertEquals("start",snapshots.decode(db.queryForObject("SELECT unlock_rule FROM story_choice WHERE id=?",String.class,choice)).path("nodeKey").asText());
  assertNotNull(releases.get(1L,10L,release.id()));assertEquals("RUNNING",play.get(1L,10L,session.id()).status());
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM player_progress WHERE project_id=?",Integer.class,p));
  assertEquals(1,saves.state(1L,10L).manual().size());
 }
 @Test void restoreRevisionAndConfirmationAreRequiredAndFailureRollsBackNewProject() {
  var saved=manual("test");
  assertThrows(BusinessException.class,()->saves.restore(1L,10L,saved.id(),new RestoreRequest(false,1L,"No")));
  assertThrows(BusinessException.class,()->saves.restore(1L,10L,saved.id(),new RestoreRequest(true,2L,"No")));
  var snapshot=saves.detail(1L,10L,saved.id()).snapshot();
  ((com.fasterxml.jackson.databind.node.ObjectNode)snapshot.path("tables").path("story_choice").get(0)).put("target_node_id",99999);
  db.update("UPDATE project_save SET content_snapshot=? WHERE id=?",snapshots.encode(snapshot),saved.id());
  assertThrows(BusinessException.class,()->saves.restore(1L,10L,saved.id(),new RestoreRequest(true,1L,"Rollback")));
  assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM narrative_project",Integer.class));
  assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM story_node",Integer.class));
 }
 @Test void endpointPermissionsAndArchivedWriteProtection() throws Exception {
  String body=json.writeValueAsString(request("test",0,null));
  mvc.perform(post("/api/projects/10/saves").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/projects/10/saves").header("Authorization","Bearer "+jwt.createToken(3L,"tester")).contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(get("/api/projects/10/saves").header("Authorization","Bearer "+jwt.createToken(4L,"outsider"))).andExpect(status().isForbidden());
  db.update("UPDATE narrative_project SET status='ARCHIVED' WHERE id=10");
  assertThrows(BusinessException.class,()->manual("archived"));
 }
 @Test void concurrentManualSavesStillLeaveExactlyFive() throws Exception {
  var pool=Executors.newFixedThreadPool(3);
  try {
   List<Callable<Summary>> tasks=new ArrayList<>();for(int i=0;i<9;i++){final int index=i;tasks.add(()->manual("concurrent"+index));}
   for(var future:pool.invokeAll(tasks))assertNotNull(future.get());
   assertEquals(5,saves.state(1L,10L).manual().size());
   assertEquals(5,db.queryForObject("SELECT COUNT(*) FROM project_save WHERE save_kind='MANUAL'",Integer.class));
  }finally{pool.shutdownNow();}
 }
 @Test void rejectsOversizedOrNonObjectDraftsWithoutCreatingHistory() {
  assertThrows(BusinessException.class,()->saves.manual(1L,10L,new SaveRequest("bad",json.createArrayNode(),0L,null)));
  var big=json.createObjectNode().put("text","a".repeat(1_000_001));
  assertThrows(BusinessException.class,()->saves.manual(1L,10L,new SaveRequest("bad",big,0L,null)));
  assertTrue(saves.state(1L,10L).manual().isEmpty());
 }
}
