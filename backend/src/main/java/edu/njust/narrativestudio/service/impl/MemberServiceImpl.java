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

    public MemberServiceImpl(ProjectMemberMapper memberMapper, UserMapper userMapper,
                             ProjectAccessService accessService) {
        this.memberMapper = memberMapper;
        this.userMapper = userMapper;
        this.accessService = accessService;
    }

    @Override
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
    public MemberDtos.Summary add(Long userId, Long projectId, MemberDtos.AddRequest request) {
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
    public MemberDtos.Summary updateRole(Long userId, Long projectId, Long memberId,
                                         MemberDtos.RoleRequest request) {
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
    public void remove(Long userId, Long projectId, Long memberId) {
        accessService.requireOwner(userId, projectId);
        ProjectMember member = requireProjectMember(projectId, memberId);
        if ("OWNER".equals(member.getMemberRole())) {
            throw BusinessException.conflict("不能移除项目创建者");
        }
        memberMapper.deleteById(memberId);
    }

    private ProjectMember requireProjectMember(Long projectId, Long memberId) {
        ProjectMember member = memberMapper.selectById(memberId);
        if (member == null || !projectId.equals(member.getProjectId())) {
            throw BusinessException.notFound("项目成员不存在");
        }
        return member;
    }

    private MemberDtos.Summary toSummary(ProjectMember member, User user) {
        if (user == null) throw BusinessException.notFound("成员用户不存在");
        return new MemberDtos.Summary(member.getId(), user.getId(), user.getUsername(), user.getDisplayName(),
                member.getMemberRole(), member.getJoinedAt());
    }
}
