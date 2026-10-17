package edu.njust.narrativestudio.service;
import edu.njust.narrativestudio.entity.NarrativeProject;
import edu.njust.narrativestudio.exception.BusinessException;
import edu.njust.narrativestudio.mapper.NarrativeProjectMapper;
import org.springframework.stereotype.Component;

/** Called inside a transaction. Serializes state/rule/session writes within a project. */
@Component
public class ProjectMutationGuard {
    private final NarrativeProjectMapper projects;
    private final ProjectAccessService access;
    public ProjectMutationGuard(NarrativeProjectMapper projects, ProjectAccessService access) {
        this.projects=projects; this.access=access;
    }
    // 【解释】先锁项目并检查成员、状态，再检查编辑权限；在事务内调用。
    public void editor(Long user,Long project) { lock(user,project); access.requireEditor(user,project); }
    // 【解释】成员写操作也需要锁和状态检查，例如模拟/反馈；不是编辑权限的同义词。
    public void member(Long user,Long project) { lock(user,project); }
    // 【解释】先取得数据库项目行锁，再检查成员和只读状态，以便并发操作看到正确顺序。
    // 【修改提示】不要交换为先普通读后加锁；项目级锁影响吞吐，但缩小锁范围需并发测试，不是简单删调用。
    private void lock(Long user,Long project) {
        NarrativeProject p=projects.lockById(project);
        // Establish the repeatable-read snapshot only AFTER acquiring the lock.
        access.requireMember(user,project);
        if(p==null || "DELETED".equals(p.getStatus())) throw BusinessException.notFound("项目不存在");
        if("ARCHIVED".equals(p.getStatus())) throw BusinessException.conflict("已归档项目只读");
    }
}
