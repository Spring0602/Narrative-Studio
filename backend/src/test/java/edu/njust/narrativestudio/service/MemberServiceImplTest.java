package edu.njust.narrativestudio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.njust.narrativestudio.dto.MemberDtos;
import edu.njust.narrativestudio.entity.ProjectMember;
import edu.njust.narrativestudio.entity.User;
import edu.njust.narrativestudio.exception.BusinessException;
import edu.njust.narrativestudio.mapper.ProjectMemberMapper;
import edu.njust.narrativestudio.mapper.UserMapper;
import edu.njust.narrativestudio.service.impl.MemberServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberServiceImplTest {
    @Mock ProjectMemberMapper memberMapper;
    @Mock UserMapper userMapper;
    @Mock ProjectAccessService accessService;
    @Mock ProjectMutationGuard guard;
    @InjectMocks MemberServiceImpl service;

    @Test
    void ownerCanInviteActiveUserAsEditor() {
        User user = user(8L, "writer", "ACTIVE");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(memberMapper.selectCount(any())).thenReturn(0L);
        when(memberMapper.insert(any(ProjectMember.class))).thenAnswer(invocation -> {
            ProjectMember member = invocation.getArgument(0);
            member.setId(20L);
            return 1;
        });

        MemberDtos.Summary result = service.add(1L, 10L, new MemberDtos.AddRequest(" writer ", "EDITOR"));

        assertEquals(20L, result.id());
        assertEquals("EDITOR", result.memberRole());
        verify(accessService).requireOwner(1L, 10L);
    }

    @Test
    void ownerMembershipCannotBeRemoved() {
        ProjectMember owner = new ProjectMember();
        owner.setId(20L);
        owner.setProjectId(10L);
        owner.setMemberRole("OWNER");
        when(memberMapper.selectById(20L)).thenReturn(owner);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.remove(1L, 10L, 20L));

        assertEquals("CONFLICT", ex.getCode());
    }

    private User user(Long id, String username, String status) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName("编剧");
        user.setStatus(status);
        return user;
    }
}
