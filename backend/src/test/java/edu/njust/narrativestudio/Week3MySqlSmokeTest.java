package edu.njust.narrativestudio;

import static org.junit.jupiter.api.Assertions.*;
import edu.njust.narrativestudio.dto.RuleDtos.*;
import edu.njust.narrativestudio.dto.StoryGraphDtos.*;
import edu.njust.narrativestudio.service.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in: existing MySQL schema; all test data is rolled back. Does not run initialization SQL. */
@EnabledIfEnvironmentVariable(named="WEEK3_MYSQL_SMOKE",matches="true")
@SpringBootTest(properties={"spring.sql.init.mode=never","debug=false","logging.level.root=WARN","logging.level.org.springframework=ERROR"})
@Transactional
class Week3MySqlSmokeTest {
    @Autowired JdbcTemplate db;
    @Autowired StoryGraphService graph;
    @Autowired RuleService rules;
    @Autowired PlaytestService playtests;
    @Autowired StoryTransferService transfer;
    @Autowired ReleaseService releases;
    @Autowired EndingCoverageService coverage;

    @Test void mysqlJsonSnapshotsRoundTripAndEndingCanBeReplayed() {
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,12);
        long user=insert("INSERT INTO sys_user(username,password_hash,display_name) VALUES(?,?,?)","w3_"+suffix,"test-only-not-a-password-hash","Week3 smoke");
        long project=insert("INSERT INTO narrative_project(name,owner_id) VALUES(?,?)","week3_smoke_"+suffix,user);
        insert("INSERT INTO project_member(project_id,user_id,member_role) VALUES(?,?,'OWNER')",project,user);
        var first=graph.createNode(user,project,new NodeRequest("start","Start",null,"NORMAL",null,true,null,null));
        var end=graph.createNode(user,project,new NodeRequest("end","End",null,"ENDING",null,false,null,null));
        var choice=graph.createChoice(user,project,first.id(),new ChoiceRequest(end.id(),"Finish",0,true));
        var variable=rules.createVariable(user,project,new VariableRequest("trust","Trust","INTEGER","30",null));
        var milestone=rules.createVariable(user,project,new VariableRequest("milestone","Milestone","BOOLEAN","false",null,"PROFILE"));
        rules.replaceRules(user,project,first.id(),choice.id(),new RulesRequest(List.of(new ConditionInput(variable.id(),"GTE","30",0)),
                List.of(new EffectInput(variable.id(),"ADD","20"),new EffectInput(milestone.id(),"SET","true")),
                new edu.njust.narrativestudio.dto.UnlockRule("VISITED",null,null,"start",null,null,null)));
        var s=playtests.start(user,project);
        var done=playtests.advance(user,project,s.id(),choice.id(),0);
        assertEquals("COMPLETED",done.status());assertEquals("50",done.state().get("trust"));
        assertEquals(100.0,coverage.get(user,project,null).coveragePercent());
        var document=transfer.exportStory(user,project);
        var imported=transfer.importStory(user,document);
        assertEquals(document,transfer.exportStory(user,imported.id()));
        var release=releases.publish(user,project);
        var frozen=playtests.startRelease(user,project,release.id());
        playtests.advance(user,project,frozen.id(),choice.id(),0);
        assertEquals(100.0,coverage.get(user,project,release.id()).coveragePercent());
        assertEquals(2,playtests.steps(user,project,s.id(),1,20).total());
        assertEquals("OBJECT",db.queryForObject("SELECT JSON_TYPE(state_after) FROM playtest_step WHERE session_id=? AND step_no=1",String.class,s.id()));
        assertEquals("50",db.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(state_after,'$.trust')) FROM playtest_step WHERE session_id=? AND step_no=1",String.class,s.id()));
        assertEquals("OBJECT",db.queryForObject("SELECT JSON_TYPE(progress_after) FROM playtest_step WHERE session_id=? AND step_no=1",String.class,s.id()));
        var repeated=playtests.start(user,project);
        assertEquals("true",repeated.state().get("milestone"));assertEquals("30",repeated.state().get("trust"));
    }
    private long insert(String sql,Object... args) {
        GeneratedKeyHolder keys=new GeneratedKeyHolder();
        db.update(connection->{PreparedStatement statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);return statement;},keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
}
