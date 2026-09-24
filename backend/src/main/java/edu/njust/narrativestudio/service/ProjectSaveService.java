package edu.njust.narrativestudio.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import edu.njust.narrativestudio.dto.ProjectSaveDtos.*;
import edu.njust.narrativestudio.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
// All authored writes acquire the same project lock. READ_COMMITTED ensures a
// waiting writer sees the preceding save before trimming the five-record window.
@Transactional(isolation=Isolation.READ_COMMITTED)
public class ProjectSaveService {
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
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public State state(Long user,Long project) {
        access.requireMember(user,project);
        var manual=db.query("SELECT * FROM project_save WHERE project_id=? AND save_kind='MANUAL' ORDER BY id DESC LIMIT 5",summary,project);
        return new State(manual,auto(user,project),snapshots.hash(snapshots.capture(project,JsonNodeFactory.instance.objectNode())));
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
        guard.editor(user,project);
        var snapshot=snapshots.capture(project,request.drafts());
        var saved=insert(user,project,"MANUAL",null,label(request.label(),"手动保存"),snapshots.hash(snapshot),snapshots.encode(snapshot));
        var old=db.queryForList("SELECT id FROM project_save WHERE project_id=? AND save_kind='MANUAL' ORDER BY id DESC",Long.class,project);
        for(int i=5;i<old.size();i++) db.update("DELETE FROM project_save WHERE id=? AND project_id=? AND save_kind='MANUAL'",old.get(i),project);
        return new SaveResult(true,saved);
    }
    public SaveResult automatic(Long user,Long project,SaveRequest request) {
        guard.editor(user,project);
        var previous=auto(user,project);
        long revision=previous==null?0:previous.revision();
        if(request.expectedRevision()==null||request.expectedRevision()!=revision)
            throw BusinessException.conflict("自动草稿已被其他窗口更新；请先查看恢复记录，不会覆盖");
        var snapshot=snapshots.capture(project,request.drafts());
        String hash=snapshots.hash(snapshot);
        if(hash.equals(previous==null?request.baselineHash():previous.hash()))return new SaveResult(false,previous);
        if(previous==null) return new SaveResult(true,insert(user,project,"AUTO",user,"自动草稿",hash,snapshots.encode(snapshot)));
        db.update("UPDATE project_save SET revision=?,content_hash=?,content_snapshot=?,saved_at=? WHERE id=?",
                Math.incrementExact(revision),hash,snapshots.encode(snapshot),LocalDateTime.now(),previous.id());
        return new SaveResult(true,auto(user,project));
    }
    public Restored restore(Long user,Long project,Long id,RestoreRequest request) {
        guard.editor(user,project);
        var saved=detail(user,project,id);
        if(!Boolean.TRUE.equals(request.confirm()) || request.expectedRevision()==null || request.expectedRevision()!=saved.summary().revision())
            throw BusinessException.conflict("恢复记录已变化，请重新预览并确认");
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
