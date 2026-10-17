package edu.njust.narrativestudio.service;

import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import edu.njust.narrativestudio.dto.ProjectSaveDtos.*;
import edu.njust.narrativestudio.dto.ProjectSaveDtos.Detail;
import edu.njust.narrativestudio.dto.ProjectSaveDtos.RestoreRequest;
import edu.njust.narrativestudio.dto.ProjectSaveDtos.Restored;
import edu.njust.narrativestudio.dto.ProjectSaveDtos.SaveRequest;
import edu.njust.narrativestudio.dto.ProjectSaveDtos.SaveResult;
import edu.njust.narrativestudio.dto.ProjectSaveDtos.State;
import edu.njust.narrativestudio.dto.ProjectSaveDtos.Summary;
import edu.njust.narrativestudio.exception.BusinessException;

@Service
// All authored writes acquire the same project lock. READ_COMMITTED ensures a
// waiting writer sees the preceding save before trimming the five-record window.
@Transactional(isolation=Isolation.READ_COMMITTED)
public class ProjectSaveService {
    // 【答辩修改导航】优先练：保留数量、默认备注、提示文字、返回字段；以下示例均未启用。
    // 保留事务隔离级别、guard 加锁、权限和修订号检查。并发正确不等于单次请求运行成功。
    private final JdbcTemplate db;
    private final AuthoringSnapshotStore snapshots;
    private final ProjectAccessService access;
    private final ProjectMutationGuard guard;
    public ProjectSaveService(JdbcTemplate db,AuthoringSnapshotStore snapshots,ProjectAccessService access,ProjectMutationGuard guard) {
        this.db=db;this.snapshots=snapshots;this.access=access;this.guard=guard;
    }
    private final RowMapper<Summary> summary=(r,n)->new Summary(r.getLong("id"),r.getLong("saved_by"),r.getString("save_kind"),
            r.getLong("revision"),r.getString("label"),r.getString("content_hash"),r.getTimestamp("saved_at").toLocalDateTime());
    private Summary auto(Long user,Long project) {
        return db.query("SELECT * FROM project_save WHERE project_id=? AND save_kind='AUTO' AND auto_slot=?",summary,project,user).stream().findFirst().orElse(null);
        //自动槽位属于当前用户
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public State state(Long user,Long project) {
        // 【常见修改 1：保留最近 10 次】本方法 SQL 的 LIMIT 5 改为 LIMIT 10，
        // 并将 manual() 中 for(int i=5;...) 改成 for(int i=10;...)；两处必须一起改。
        // 同步 detail 的“最近5次”提示、前端 ProjectSaveBar.vue 的提示文字、相关测试和文档。
        // database/database-design/verify-history.sql 的保留数量检查也要同步。
        // 单改 LIMIT 只能影响显示，不能改变真正保留数量；已经删除的旧记录不会因改成 10 而回来。
        access.requireMember(user,project);//检查用户是不是项目成员
        var manual=db.query("SELECT * FROM project_save WHERE project_id=? AND save_kind='MANUAL' ORDER BY id DESC LIMIT 5",summary,project);//按 ID 倒序取最近 5 条手动记录。
        return new State(manual,auto(user,project),snapshots.hash(snapshots.capture(project,JsonNodeFactory.instance.objectNode())));
        //查询当前用户自己的自动草稿。收集当前项目内容，计算摘要。一起返回前端。
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Detail detail(Long user,Long project,Long id) {
        access.requireMember(user,project);
        var rows=db.query("SELECT * FROM project_save WHERE project_id=? AND id=?",summary,project,id);
        if(rows.isEmpty())throw BusinessException.notFound("保存记录不存在或已超出最近5次保留范围");
        var saved=rows.getFirst();
        if(saved.kind().equals("AUTO")&&!saved.savedBy().equals(user))throw BusinessException.forbidden("只能读取本人的自动草稿");
        String content=db.queryForObject("SELECT content_snapshot FROM project_save WHERE id=?",String.class,id);
        return new Detail(saved,snapshots.decode(content));
    }
    public SaveResult manual(Long user,Long project,SaveRequest request) {
        // 【常见修改 2：默认备注】下面 label(request.label(),"手动保存") 可改为
        // label(request.label(),"未命名版本")。仅影响以后备注为空的手动保存，不改已有记录。
        // 验证空字符串/纯空白使用默认备注，用户填写的备注仍保留；自动草稿名称是另一处配置。
        guard.editor(user,project);
        var snapshot=snapshots.capture(project,request.drafts());
        var saved=insert(user,project,"MANUAL",null,label(request.label(),"手动保存"),snapshots.hash(snapshot),snapshots.encode(snapshot));
        var old=db.queryForList("SELECT id FROM project_save WHERE project_id=? AND save_kind='MANUAL' ORDER BY id DESC",Long.class,project);
        // 【保留数量的实际执行点】列表从新到旧排列，索引从 0 开始，所以 i=5 从第 6 条开始删。
        // 改为保留 10 条时用 i=10，不是 i=9。测试连续保存 11 次，留下最新 10 条且自动草稿仍在。
        // 减小保留数量会在后续手动保存时删除更多旧记录，不能把这种改动当作只改界面。
        for(int i=5;i<old.size();i++) db.update("DELETE FROM project_save WHERE id=? AND project_id=? AND save_kind='MANUAL'",old.get(i),project);
        //删除第五条之后的旧手动记录
        return new SaveResult(true,saved);
    }
    public SaveResult automatic(Long user,Long project,SaveRequest request) {
        // 【常见修改 3：自动保存间隔】不在此方法加定时器。
        // 到 frontend/src/utils/autosaveClock.ts，将 AUTOSAVE_INTERVAL=10*60*1000
        // 改为 AUTOSAVE_INTERVAL=5*60*1000 即 5 分钟，并同步 ProjectSaveBar.vue 的文字和时钟测试。
        // 组件里的 setInterval(...,10000) 是每 10 秒检查是否到期，不是每 10 秒就保存。
        // 【保护逻辑】修订号检查防旧窗口覆盖，hash 比较跳过无变化内容，两者都要保留。
        // “只保留一个自动槽位”不同于手动记录数量，不能靠改上面的 5 来增加自动草稿数量。
        guard.editor(user,project);
        var previous=auto(user,project);
        long revision=previous==null?0:previous.revision();
        //当前请求是否来自过期窗口
        if(request.expectedRevision()==null||request.expectedRevision()!=revision)
            throw BusinessException.conflict("自动草稿已被其他窗口更新；请先查看恢复记录，不会覆盖");
        var snapshot=snapshots.capture(project,request.drafts());
        String hash=snapshots.hash(snapshot);
        /*已经有自动草稿：与旧自动草稿的摘要比较。
        尚无自动草稿：与请求中的初始基准摘要baselineHash 比较。 */
        if(hash.equals(previous==null?request.baselineHash():previous.hash()))return new SaveResult(false,previous);
        //摘要相同：返回 changed=false，不写入。
        if(previous==null) return new SaveResult(true,insert(user,project,"AUTO",user,"自动草稿",hash,snapshots.encode(snapshot)));
        //摘要不同且没有旧草稿：插入第一条自动草稿。
        db.update("UPDATE project_save SET revision=?,content_hash=?,content_snapshot=?,saved_at=? WHERE id=?",
                Math.incrementExact(revision),hash,snapshots.encode(snapshot),LocalDateTime.now(),previous.id());
        //摘要不同且已有旧草稿：更新原记录，修订号加 1。
        return new SaveResult(true,auto(user,project));
    }
    public Restored restore(Long user,Long project,Long id,RestoreRequest request) {
        // 【常见修改 4：恢复提示】可只修改下方 conflict 的提示字符串，保留 confirm 和 revision 判断。
        // 改为“恢复原项目”不是修改一个提示或 SQL：会涉及历史运行引用、事务及数据覆盖风险，不属小改动。
        // 默认恢复名称由前端 ProjectSaveBar.vue 拼接“ · 恢复副本”，后端接收用户最终确认的 name。
        guard.editor(user,project);
        var saved=detail(user,project,id);
        if(!Boolean.TRUE.equals(request.confirm()) || request.expectedRevision()==null || request.expectedRevision()!=saved.summary().revision())
            throw BusinessException.conflict("恢复记录已变化，请重新预览并确认");
        //检查用户已明确确认
        // Original project, all five manual records and the auto draft remain untouched.
        var copied=snapshots.copy(user,request.name(),saved.snapshot());
        return new Restored(copied.project(),copied.drafts());
    }
    private String label(String value,String fallback) {return value==null||value.isBlank()?fallback:value.trim();}
    private Summary insert(Long user,Long project,String kind,Long slot,String label,String hash,String content) {
        var keys=new GeneratedKeyHolder();
        db.update(c->{var p=c.prepareStatement("INSERT INTO project_save(project_id,saved_by,save_kind,auto_slot,revision,label,content_hash,content_snapshot,saved_at) VALUES(?,?,?,?,1,?,?,?,?)",new String[]{"id"});
            p.setLong(1,project);p.setLong(2,user);p.setString(3,kind);p.setObject(4,slot);p.setString(5,label);p.setString(6,hash);p.setString(7,content);p.setObject(8,LocalDateTime.now());return p;},keys);
        Long id=Objects.requireNonNull(keys.getKey()).longValue();
        return db.queryForObject("SELECT * FROM project_save WHERE id=?",summary,id);
    }
}
