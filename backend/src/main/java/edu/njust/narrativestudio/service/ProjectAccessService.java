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

    // 【解释】项目存在且用户有成员记录即可读取。
    public ProjectMember requireMember(Long userId, Long projectId) {
        requireProject(projectId);
        ProjectMember member = findMember(userId, projectId);
        if (member == null) throw BusinessException.forbidden("无权访问该项目");
        return member;
    }

    // 【解释】要求 OWNER/EDITOR 且项目未归档；被多个模块共用。
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

    // 【解释】先执行编辑检查，再限定 OWNER。
    // 【修改提示】提示文字可泛化为“只有项目所有者可以执行此操作”；该方法不只管理成员，也用于发布等入口。
    public ProjectMember requireOwner(Long userId, Long projectId) {
        ProjectMember member = requireEditor(userId, projectId);
        if (!"OWNER".equals(member.getMemberRole())) {
            throw BusinessException.forbidden("只有项目创建者可以管理成员");
        }
        return member;
    }

    // 【解释】拒绝不存在/已删除的项目，归档项目仍可供只读业务查询。
    public NarrativeProject requireProject(Long projectId) {
        NarrativeProject project = projectMapper.selectById(projectId);
        if (project == null || "DELETED".equals(project.getStatus())) {
            throw BusinessException.notFound("项目不存在");
        }
        return project;
    }

    // 【解释】按用户与项目组合查成员，不通过全局用户角色推断项目角色。
    private ProjectMember findMember(Long userId, Long projectId) {
        return memberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId));
    }
}
