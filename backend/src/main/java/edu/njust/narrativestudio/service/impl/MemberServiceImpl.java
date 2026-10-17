package edu.njust.narrativestudio.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.njust.narrativestudio.dto.MemberDtos;
import edu.njust.narrativestudio.entity.ProjectMember;
import edu.njust.narrativestudio.entity.User;
import edu.njust.narrativestudio.exception.BusinessException;
import edu.njust.narrativestudio.mapper.ProjectMemberMapper;
import edu.njust.narrativestudio.mapper.UserMapper;
import edu.njust.narrativestudio.service.MemberService;
import edu.njust.narrativestudio.service.ProjectAccessService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberServiceImpl implements MemberService {
    private final ProjectMemberMapper memberMapper;
    private final UserMapper userMapper;
    private final ProjectAccessService accessService;
    private final edu.njust.narrativestudio.service.ProjectMutationGuard guard;

    public MemberServiceImpl(ProjectMemberMapper memberMapper, UserMapper userMapper,
                             ProjectAccessService accessService, edu.njust.narrativestudio.service.ProjectMutationGuard guard) {
        this.memberMapper = memberMapper;
        this.userMapper = userMapper;
        this.accessService = accessService;
        this.guard = guard;
    }

    @Override
    // 【解释】查项目成员，批量查用户，再合并昵称等信息，避免每个成员单独查一次用户。
    // 【常见修改】最新加入在前可把 orderByAsc(ProjectMember::getId) 换为 orderByDesc(ProjectMember::getJoinedAt).orderByDesc(ProjectMember::getId)。
    public List<MemberDtos.Summary> list(Long userId, Long projectId) {
        accessService.requireMember(userId, projectId);
        List<ProjectMember> members = memberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .orderByAsc(ProjectMember::getId));
        if (members.isEmpty()) return List.of();
        Map<Long, User> users = userMapper.selectBatchIds(members.stream().map(ProjectMember::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return members.stream().map(member -> toSummary(member, users.get(member.getUserId()))).toList();
    }

    @Override
    @Transactional
    // 【解释】锁定项目且要求 OWNER；只能邀请已注册且 ACTIVE 的用户，拒绝重复成员，再保存角色。
    // 【修改提示】允许角色在 MemberDtos 中配置；不要直接开放 OWNER，否则涉及唯一所有者和 owner_id 一致性。
    public MemberDtos.Summary add(Long userId, Long projectId, MemberDtos.AddRequest request) {
        guard.editor(userId,projectId);
        accessService.requireOwner(userId, projectId);
        User invited = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.username().trim()));
        if (invited == null || !"ACTIVE".equals(invited.getStatus())) {
            throw BusinessException.notFound("待邀请用户不存在或已停用");
        }
        Long count = memberMapper.selectCount(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, invited.getId()));
        if (count > 0) throw BusinessException.conflict("该用户已是项目成员");

        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setUserId(invited.getId());
        member.setMemberRole(request.memberRole());
        member.setJoinedAt(LocalDateTime.now());
        memberMapper.insert(member);
        return toSummary(member, invited);
    }

    @Override
    @Transactional
    // 【解释】OWNER 修改普通成员角色；先验证成员属于当前项目，并禁止修改创建者角色。
    public MemberDtos.Summary updateRole(Long userId, Long projectId, Long memberId,
                                         MemberDtos.RoleRequest request) {
        guard.editor(userId,projectId);
        accessService.requireOwner(userId, projectId);
        ProjectMember member = requireProjectMember(projectId, memberId);
        if ("OWNER".equals(member.getMemberRole())) {
            throw BusinessException.conflict("不能修改项目创建者的角色");
        }
        member.setMemberRole(request.memberRole());
        memberMapper.updateById(member);
        return toSummary(member, userMapper.selectById(member.getUserId()));
    }

    @Override
    @Transactional
    // 【解释】OWNER 移除普通成员，不能移除创建者；删除的是项目成员关系，不是系统用户。
    public void remove(Long userId, Long projectId, Long memberId) {
        guard.editor(userId,projectId);
        accessService.requireOwner(userId, projectId);
        ProjectMember member = requireProjectMember(projectId, memberId);
        if ("OWNER".equals(member.getMemberRole())) {
            throw BusinessException.conflict("不能移除项目创建者");
        }
        memberMapper.deleteById(memberId);
    }

    // 【解释】成员记录 ID 必须属于本项目，防止跨项目修改他人身份。
    private ProjectMember requireProjectMember(Long projectId, Long memberId) {
        ProjectMember member = memberMapper.selectById(memberId);
        if (member == null || !projectId.equals(member.getProjectId())) {
            throw BusinessException.notFound("项目成员不存在");
        }
        return member;
    }

    // 【解释】把成员记录与用户资料组装为响应；用户丢失时报错，不静默输出损坏的成员关系。
    private MemberDtos.Summary toSummary(ProjectMember member, User user) {
        if (user == null) throw BusinessException.notFound("成员用户不存在");
        return new MemberDtos.Summary(member.getId(), user.getId(), user.getUsername(), user.getDisplayName(),
                member.getMemberRole(), member.getJoinedAt());
    }
}
