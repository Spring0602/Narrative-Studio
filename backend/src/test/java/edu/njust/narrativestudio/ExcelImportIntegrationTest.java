package edu.njust.narrativestudio;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.njust.narrativestudio.config.JwtService;
import edu.njust.narrativestudio.dto.ExcelImportDtos.*;
import edu.njust.narrativestudio.service.*;
import edu.njust.narrativestudio.exception.BusinessException;
import java.io.ByteArrayOutputStream;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:excel;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:week3-schema.sql,classpath:database-extension-test.sql",
    "debug=false","logging.level.root=WARN","logging.level.org.springframework=ERROR"
})
@AutoConfigureMockMvc
class ExcelImportIntegrationTest {
    @Autowired ExcelStoryParser parser;
    @Autowired ExcelImportService imports;
    @Autowired StoryGraphService graph;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @BeforeEach void setup() {
        for(String table:List.of("player_progress","account_action_token","test_feedback","playtest_step","playtest_session","story_release",
                "story_choice_draft","state_effect","choice_condition","story_choice","node_character","character_knowledge","character_relation",
                "character_profile","world_entry","detected_issue","story_node","state_variable","project_member","narrative_project","sys_user"))
            db.update("DELETE FROM "+table);
        db.update("INSERT INTO sys_user(id,username,password_hash,display_name) VALUES(1,'author','not-login','Author'),(2,'tester','not-login','Tester'),(3,'outsider','not-login','Other')");
        db.update("INSERT INTO narrative_project(id,name,owner_id) VALUES(10,'Empty project',1)");
        db.update("INSERT INTO project_member(project_id,user_id,member_role) VALUES(10,1,'OWNER'),(10,2,'TESTER')");
    }
    MockMultipartFile workbook() throws Exception {
        try(var book=new XSSFWorkbook()) {
            var s=book.createSheet("节点");
            String[][] cells={{"id","title","type","next"},{"s","开篇","普通","e"},{"e","结局","结局",""}};
            for(int r=0;r<cells.length;r++)for(int c=0;c<cells[r].length;c++){
                var row=s.getRow(r);if(row==null)row=s.createRow(r);row.createCell(c).setCellValue(cells[r][c]);
            }
            var out=new ByteArrayOutputStream();book.write(out);
            return new MockMultipartFile("file","story.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",out.toByteArray());
        }
    }
    Request request() {return new Request("Imported","From Excel",
            new Mapping("节点",1,Map.of("nodeKey",0,"title",1,"nodeType",2),null,1,Map.of(),
                    List.of(new Branch(3,null)),Map.of(),null,true),null,null);}
    Request confirmed(MockMultipartFile file) {
        var r=request();return new Request(r.name(),r.description(),r.mapping(),parser.preview(file,r).digest(),true);
    }
    MockMultipartFile options(Request r) throws Exception {return new MockMultipartFile("options","","application/json",json.writeValueAsBytes(r));}
    String token(long user) {return "Bearer "+jwt.createToken(user,user==1?"author":user==2?"tester":"outsider",0L);}
    @Test void multipartInspectAndPreviewRequireLoginAndDoNotWrite() throws Exception {
        var f=workbook();
        mvc.perform(multipart("/api/story-excel/inspect").file(f)).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/story-excel/inspect").file(f).header("Authorization",token(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sheets[0].name").value("节点"));
        mvc.perform(multipart("/api/story-excel/preview").file(f).file(options(request())).header("Authorization",token(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.document.nodes.length()").value(2))
                .andExpect(jsonPath("$.data.document.choices.length()").value(1));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM story_node",Integer.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM narrative_project",Integer.class));
    }
    @Test void confirmsIntoCurrentEmptyGraphAndRefusesOverwrite() throws Exception {
        var f=workbook();var r=confirmed(f);
        mvc.perform(multipart("/api/projects/10/import-excel").file(f).file(options(r)).header("Authorization",token(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(10));
        assertEquals(2,graph.getGraph(1L,10L).nodes().size());assertEquals(1,graph.getGraph(1L,10L).choices().size());
        assertEquals("Empty project",db.queryForObject("SELECT name FROM narrative_project WHERE id=10",String.class));
        assertThrows(BusinessException.class,()->imports.intoEmpty(1L,10L,f,r));
        assertEquals(2,graph.getGraph(1L,10L).nodes().size());
    }
    @Test void createsNewOwnedProjectWithoutTouchingExistingProject() throws Exception {
        var f=workbook();
        var result=imports.create(1L,f,confirmed(f));
        assertNotEquals(10L,result.id());
        assertEquals(1L,result.ownerId());assertEquals("OWNER",result.memberRole());
        assertEquals(2,graph.getGraph(1L,result.id()).nodes().size());
        assertTrue(graph.getGraph(1L,10L).nodes().isEmpty());
    }
    @Test void stalePreviewOrMissingConfirmationCannotCreateAnything() throws Exception {
        var f=workbook();var r=confirmed(f);
        assertThrows(BusinessException.class,()->imports.create(1L,f,request()));
        var changed=new Request("Changed name",r.description(),r.mapping(),r.expectedDigest(),true);
        assertThrows(BusinessException.class,()->imports.create(1L,f,changed));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM narrative_project",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM story_node",Integer.class));
    }
    @Test void rejectsTesterOutsiderArchivedAndMissingUploadParts() throws Exception {
        var f=workbook();var r=options(confirmed(f));
        mvc.perform(multipart("/api/projects/10/import-excel").file(f).file(r).header("Authorization",token(2))).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/projects/10/import-excel").file(f).file(r).header("Authorization",token(3))).andExpect(status().isForbidden());
        db.update("UPDATE narrative_project SET status='ARCHIVED' WHERE id=10");
        mvc.perform(multipart("/api/projects/10/import-excel").file(f).file(r).header("Authorization",token(1))).andExpect(status().isConflict());
        mvc.perform(multipart("/api/story-excel/preview").file(f).header("Authorization",token(1))).andExpect(status().isBadRequest());
    }
    @Test void databaseFailureRollsBackProjectNodesAndChoices() throws Exception {
        var f=workbook();var r=confirmed(f);
        db.execute("ALTER TABLE story_choice ADD CONSTRAINT excel_forced_failure CHECK(choice_text <> '继续')");
        try {
            assertThrows(Exception.class,()->imports.create(1L,f,r));
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM narrative_project",Integer.class));
            assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM story_node",Integer.class));
            assertThrows(Exception.class,()->imports.intoEmpty(1L,10L,f,r));
            assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM story_node",Integer.class));
        } finally {db.execute("ALTER TABLE story_choice DROP CONSTRAINT excel_forced_failure");}
    }
}
