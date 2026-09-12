package edu.njust.narrativestudio.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.njust.narrativestudio.dto.ProjectDtos;
import edu.njust.narrativestudio.entity.NarrativeProject;
import edu.njust.narrativestudio.entity.ProjectMember;
import edu.njust.narrativestudio.exception.BusinessException;
import edu.njust.narrativestudio.mapper.NarrativeProjectMapper;
import edu.njust.narrativestudio.mapper.ProjectMemberMapper;
import edu.njust.narrativestudio.service.ProjectService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectServiceImpl implements ProjectService {
    private final NarrativeProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;

    public ProjectServiceImpl(NarrativeProjectMapper projectMapper, ProjectMemberMapper memberMapper) {
        this.projectMapper = projectMapper;
        this.memberMapper = memberMapper;
    }

    @Override
    @Transactional
    public ProjectDtos.Summary create(Long userId, ProjectDtos.CreateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        NarrativeProject project = new NarrativeProject();
        project.setName(request.name().trim());
        project.setDescription(trimToNull(request.description()));
        project.setOwnerId(userId);
        project.setStatus("ACTIVE");
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        projectMapper.insert(project);

        ProjectMember owner = new ProjectMember();
        owner.setProjectId(project.getId());
        owner.setUserId(userId);
        owner.setMemberRole("OWNER");
        owner.setJoinedAt(now);
        memberMapper.insert(owner);
        return toSummary(project, "OWNER");
    }

    @Override
    public List<ProjectDtos.Summary> listAccessible(Long userId) {
        List<ProjectMember> memberships = memberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getUserId, userId));
        if (memberships.isEmpty()) return List.of();
        Map<Long, ProjectMember> byProject = memberships.stream().collect(Collectors.toMap(
                ProjectMember::getProjectId, Function.identity(), (a, b) -> a));
        return projectMapper.selectList(new LambdaQueryWrapper<NarrativeProject>()
                        .in(NarrativeProject::getId, byProject.keySet())
                        .ne(NarrativeProject::getStatus, "DELETED")
                        .orderByDesc(NarrativeProject::getUpdatedAt))
                .stream().map(p -> toSummary(p, byProject.get(p.getId()).getMemberRole())).toList();
    }

    @Override
    public ProjectDtos.Summary getAccessible(Long userId, Long projectId) {
        NarrativeProject project = requireProject(projectId);
        ProjectMember membership = requireMembership(userId, projectId);
        return toSummary(project, membership.getMemberRole());
    }

    @Override
    @Transactional
    public ProjectDtos.Summary update(Long userId, Long projectId, ProjectDtos.UpdateRequest request) {
        projectMapper.lockById(projectId);
        NarrativeProject project = requireProject(projectId);
        ProjectMember membership = requireEditableMembership(userId, projectId);
        if ("ARCHIVED".equals(project.getStatus())) {
            throw BusinessException.conflict("已归档项目不可编辑");
        }
        project.setName(request.name().trim());
        project.setDescription(trimToNull(request.description()));
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);
        return toSummary(project, membership.getMemberRole());
    }

    @Override
    @Transactional
    public void archive(Long userId, Long projectId) {
        projectMapper.lockById(projectId);
        NarrativeProject project = requireProject(projectId);
        ProjectMember membership = requireMembership(userId, projectId);
        if (!"OWNER".equals(membership.getMemberRole())) {
            throw BusinessException.forbidden("只有项目创建者可以归档项目");
        }
        project.setStatus("ARCHIVED");
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);
    }

    private NarrativeProject requireProject(Long projectId) {
        NarrativeProject project = projectMapper.selectById(projectId);
        if (project == null || "DELETED".equals(project.getStatus())) {
            throw BusinessException.notFound("项目不存在");
        }
        return project;
    }

    private ProjectMember requireMembership(Long userId, Long projectId) {
        ProjectMember membership = memberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId).eq(ProjectMember::getUserId, userId));
        if (membership == null) throw BusinessException.forbidden("无权访问该项目");
        return membership;
    }

    private ProjectMember requireEditableMembership(Long userId, Long projectId) {
        ProjectMember membership = requireMembership(userId, projectId);
        if (!List.of("OWNER", "EDITOR").contains(membership.getMemberRole())) {
            throw BusinessException.forbidden("当前成员角色无编辑权限");
        }
        return membership;
    }

    private ProjectDtos.Summary toSummary(NarrativeProject project, String memberRole) {
        return new ProjectDtos.Summary(project.getId(), project.getName(), project.getDescription(),
                project.getOwnerId(), project.getStatus(), memberRole, project.getUpdatedAt());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
