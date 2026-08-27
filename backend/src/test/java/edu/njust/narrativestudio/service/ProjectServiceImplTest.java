package edu.njust.narrativestudio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.njust.narrativestudio.dto.ProjectDtos;
import edu.njust.narrativestudio.entity.NarrativeProject;
import edu.njust.narrativestudio.entity.ProjectMember;
import edu.njust.narrativestudio.exception.BusinessException;
import edu.njust.narrativestudio.mapper.NarrativeProjectMapper;
import edu.njust.narrativestudio.mapper.ProjectMemberMapper;
import edu.njust.narrativestudio.service.impl.ProjectServiceImpl;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ProjectServiceImplTest {
    @Mock NarrativeProjectMapper projectMapper;
    @Mock ProjectMemberMapper memberMapper;
    private ProjectServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ProjectServiceImpl(projectMapper, memberMapper);
    }

    @Test
    void createAlsoCreatesOwnerMembership() {
        when(projectMapper.insert(any(NarrativeProject.class))).thenAnswer(invocation -> {
            NarrativeProject project = invocation.getArgument(0);
            project.setId(10L);
            return 1;
        });

        ProjectDtos.Summary result = service.create(7L, new ProjectDtos.CreateRequest(" 测试项目 ", " 简介 "));

        assertEquals(10L, result.id());
        assertEquals("测试项目", result.name());
        assertEquals("OWNER", result.memberRole());
        verify(memberMapper).insert(any(ProjectMember.class));
    }

    @Test
    void testerCannotUpdateProject() {
        when(projectMapper.selectById(10L)).thenReturn(project(10L));
        ProjectMember tester = new ProjectMember();
        tester.setProjectId(10L);
        tester.setUserId(7L);
        tester.setMemberRole("TESTER");
        when(memberMapper.selectOne(any())).thenReturn(tester);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(7L, 10L, new ProjectDtos.UpdateRequest("新名称", null)));

        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    void editorCannotArchiveProject() {
        when(projectMapper.selectById(10L)).thenReturn(project(10L));
        ProjectMember editor = new ProjectMember();
        editor.setProjectId(10L);
        editor.setUserId(7L);
        editor.setMemberRole("EDITOR");
        when(memberMapper.selectOne(any())).thenReturn(editor);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.archive(7L, 10L));

        assertEquals("FORBIDDEN", ex.getCode());
    }

    private NarrativeProject project(Long id) {
        NarrativeProject project = new NarrativeProject();
        project.setId(id);
        project.setName("项目");
        project.setOwnerId(1L);
        project.setStatus("ACTIVE");
        project.setUpdatedAt(LocalDateTime.now());
        return project;
    }
}
