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
    public void editor(Long user,Long project) { lock(user,project); access.requireEditor(user,project); }
    public void member(Long user,Long project) { lock(user,project); }
    private void lock(Long user,Long project) {
        NarrativeProject p=projects.lockById(project);
        // Establish the repeatable-read snapshot only AFTER acquiring the lock.
        access.requireMember(user,project);
        if(p==null || "DELETED".equals(p.getStatus())) throw BusinessException.notFound("项目不存在");
        if("ARCHIVED".equals(p.getStatus())) throw BusinessException.conflict("已归档项目只读");
    }
}
