package edu.njust.narrativestudio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import edu.njust.narrativestudio.entity.NarrativeProject;
import edu.njust.narrativestudio.entity.ProjectMember;
import edu.njust.narrativestudio.exception.BusinessException;
import edu.njust.narrativestudio.mapper.NarrativeProjectMapper;
import edu.njust.narrativestudio.mapper.ProjectMemberMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectAccessServiceTest {
    @Mock NarrativeProjectMapper projectMapper;
    @Mock ProjectMemberMapper memberMapper;
    @InjectMocks ProjectAccessService service;

    @Test
    void testerCannotEdit() {
        when(projectMapper.selectById(10L)).thenReturn(project("ACTIVE"));
        when(memberMapper.selectOne(any())).thenReturn(member("TESTER"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.requireEditor(7L, 10L));

        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    void archivedProjectCannotBeEditedByOwner() {
        when(projectMapper.selectById(10L)).thenReturn(project("ARCHIVED"));
        when(memberMapper.selectOne(any())).thenReturn(member("OWNER"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.requireEditor(7L, 10L));

        assertEquals("CONFLICT", ex.getCode());
    }

    private NarrativeProject project(String status) {
        NarrativeProject project = new NarrativeProject();
        project.setId(10L);
        project.setStatus(status);
        return project;
    }

    private ProjectMember member(String role) {
        ProjectMember member = new ProjectMember();
        member.setProjectId(10L);
        member.setUserId(7L);
        member.setMemberRole(role);
        return member;
    }
}
