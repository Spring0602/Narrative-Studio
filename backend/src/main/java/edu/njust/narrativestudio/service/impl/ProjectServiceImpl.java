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
    // 【解释】创建 ACTIVE 项目并插入 OWNER 成员，两次写入在同一事务中，避免只有项目没有所有者。
    // 【常见修改】名称上限在 ProjectDtos.CreateRequest；新增、修改、导入和恢复入口需一起核对。
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
    // 【解释】先查询我的成员关系，再限定项目 ID 查询未删除项目，最后把我的角色组装到响应。
    // 【常见修改】按创建时间倒序：把 orderByDesc(NarrativeProject::getUpdatedAt) 替换为 orderByDesc(NarrativeProject::getCreatedAt)。
    // 可追加 ID 作为相同时间的稳定排序；不能删 .in(...) 来显示其他人的项目。
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
    // 【解释】分别确认项目存在、用户是成员，返回项目概要与当前用户在项目中的角色。
    public ProjectDtos.Summary getAccessible(Long userId, Long projectId) {
        NarrativeProject project = requireProject(projectId);
        ProjectMember membership = requireMembership(userId, projectId);
        return toSummary(project, membership.getMemberRole());
    }

    @Override
    @Transactional
    // 【解释】锁住项目，确认 OWNER/EDITOR 且未归档，再修改名称描述和更新时间。
    // 【修改提示】只允许 OWNER 修改时在本入口增加角色限制，不要全局删除 EDITOR 的编辑能力。
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
    // 【解释】只有 OWNER 可以把状态设为 ARCHIVED，是归档而非删表/删记录；归档后写入受其他业务入口限制。
    // 【修改提示】归档提示可改；支持取消归档需单独设计权限和状态转换接口，不能让前端随便传任意状态。
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

    // 【解释】统一拒绝不存在或 DELETED 项目，不以知道 ID 作为有访问权限的依据。
    private NarrativeProject requireProject(Long projectId) {
        NarrativeProject project = projectMapper.selectById(projectId);
        if (project == null || "DELETED".equals(project.getStatus())) {
            throw BusinessException.notFound("项目不存在");
        }
        return project;
    }

    // 【解释】按 projectId 与 userId 同时查成员；没有记录就拒绝访问。
    private ProjectMember requireMembership(Long userId, Long projectId) {
        ProjectMember membership = memberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId).eq(ProjectMember::getUserId, userId));
        if (membership == null) throw BusinessException.forbidden("无权访问该项目");
        return membership;
    }

    // 【解释】成员中只有 OWNER、EDITOR 能编辑；TESTER 是项目角色，不等于系统管理员角色。
    private ProjectMember requireEditableMembership(Long userId, Long projectId) {
        ProjectMember membership = requireMembership(userId, projectId);
        if (!List.of("OWNER", "EDITOR").contains(membership.getMemberRole())) {
            throw BusinessException.forbidden("当前成员角色无编辑权限");
        }
        return membership;
    }

    // 【解释】实体转返回 DTO，不修改数据库。
    // 【常见修改】追加创建时间：ProjectDtos.Summary 增加 LocalDateTime createdAt，并在这里构造参数末尾追加 project.getCreatedAt()，同步其他调用。
    private ProjectDtos.Summary toSummary(NarrativeProject project, String memberRole) {
        return new ProjectDtos.Summary(project.getId(), project.getName(), project.getDescription(),
                project.getOwnerId(), project.getStatus(), memberRole, project.getUpdatedAt());
    }

    // 【解释】可选文本为空/纯空白转 null，其余去首尾空白；改变此规则会影响新增和修改。
    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
