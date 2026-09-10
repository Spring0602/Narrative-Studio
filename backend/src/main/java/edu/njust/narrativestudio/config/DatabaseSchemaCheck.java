package edu.njust.narrativestudio.config;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/** Read-only fail-fast check; initialization and migration remain explicit Navicat operations. */
@Component
@ConditionalOnProperty(name="app.database.schema-check",havingValue="true",matchIfMissing=true)
public class DatabaseSchemaCheck implements ApplicationRunner {
    private final JdbcTemplate db;
    public DatabaseSchemaCheck(JdbcTemplate db) {this.db=db;}
    @Override public void run(ApplicationArguments args) {
        try {
            for(String columns:List.of(
                "email,email_verified_at,token_version FROM sys_user",
                "user_id,purpose,token_hash,destination_email,expires_at,used_at FROM account_action_token",
                "project_id,source_node_id,choice_text,created_by FROM story_choice_draft",
                "project_id,version_no,schema_version,content_snapshot FROM story_release",
                "release_id FROM playtest_session","knowledge_before,knowledge_after FROM playtest_step",
                "step_id FROM test_feedback")) db.queryForList("SELECT "+columns+" WHERE 1=0");
        } catch(org.springframework.dao.DataAccessException ex) {
            throw new IllegalStateException("数据库缺少20表扩展或无法读取。请核对 DB_URL 和账号权限，按 database/database-design/README.md 在备份及测试验证后完成升级；应用不会自动执行DDL。",ex);
        }
    }
}
