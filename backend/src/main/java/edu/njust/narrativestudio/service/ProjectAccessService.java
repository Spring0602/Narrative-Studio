package edu.njust.narrativestudio.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.njust.narrativestudio.entity.NarrativeProject;
import edu.njust.narrativestudio.entity.ProjectMember;
import edu.njust.narrativestudio.exception.BusinessException;
import edu.njust.narrativestudio.mapper.NarrativeProjectMapper;
import edu.njust.narrativestudio.mapper.ProjectMemberMapper;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ProjectAccessService {
    private static final Set<String> EDIT_ROLES = Set.of("OWNER", "EDITOR");

    private final NarrativeProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;

    public ProjectAccessService(NarrativeProjectMapper projectMapper, ProjectMemberMapper memberMapper) {
        this.projectMapper = projectMapper;
        this.memberMapper = memberMapper;
    }

    public ProjectMember requireMember(Long userId, Long projectId) {
        requireProject(projectId);
        ProjectMember member = findMember(userId, projectId);
        if (member == null) throw BusinessException.forbidden("无权访问该项目");
        return member;
    }

    public ProjectMember requireEditor(Long userId, Long projectId) {
        NarrativeProject project = requireProject(projectId);
        ProjectMember member = findMember(userId, projectId);
        if (member == null || !EDIT_ROLES.contains(member.getMemberRole())) {
            throw BusinessException.forbidden("当前成员角色无编辑权限");
        }
        if ("ARCHIVED".equals(project.getStatus())) {
            throw BusinessException.conflict("已归档项目不可编辑");
        }
        return member;
    }

    public ProjectMember requireOwner(Long userId, Long projectId) {
        ProjectMember member = requireEditor(userId, projectId);
        if (!"OWNER".equals(member.getMemberRole())) {
            throw BusinessException.forbidden("只有项目创建者可以管理成员");
        }
        return member;
    }

    public NarrativeProject requireProject(Long projectId) {
        NarrativeProject project = projectMapper.selectById(projectId);
        if (project == null || "DELETED".equals(project.getStatus())) {
            throw BusinessException.notFound("项目不存在");
        }
        return project;
    }

    private ProjectMember findMember(Long userId, Long projectId) {
        return memberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId));
    }
}
