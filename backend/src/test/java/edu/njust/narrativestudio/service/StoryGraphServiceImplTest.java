package edu.njust.narrativestudio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.njust.narrativestudio.dto.StoryGraphDtos;
import edu.njust.narrativestudio.entity.StoryNode;
import edu.njust.narrativestudio.exception.BusinessException;
import edu.njust.narrativestudio.mapper.StoryChoiceMapper;
import edu.njust.narrativestudio.mapper.StoryNodeMapper;
import edu.njust.narrativestudio.service.impl.StoryGraphServiceImpl;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoryGraphServiceImplTest {
    @Mock StoryNodeMapper nodeMapper;
    @Mock StoryChoiceMapper choiceMapper;
    @Mock ProjectAccessService accessService;
    private StoryGraphServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StoryGraphServiceImpl(nodeMapper, choiceMapper, accessService,
            org.mockito.Mockito.mock(edu.njust.narrativestudio.service.ReleaseService.class),
            org.mockito.Mockito.mock(edu.njust.narrativestudio.service.ProjectMutationGuard.class));
    }

    @Test
    void createNodeNormalizesFieldsAndDefaultsPosition() {
        when(nodeMapper.selectCount(any())).thenReturn(0L);
        when(nodeMapper.insert(any(StoryNode.class))).thenAnswer(invocation -> {
            StoryNode node = invocation.getArgument(0);
            node.setId(20L);
            return 1;
        });

        StoryGraphDtos.NodeSummary result = service.createNode(7L, 10L,
                nodeRequest(" start ", " 开场 ", "NORMAL", false));

        assertEquals(20L, result.id());
        assertEquals("start", result.nodeKey());
        assertEquals("开场", result.title());
        assertEquals(BigDecimal.ZERO, result.positionX());
        verify(accessService).requireEditor(7L, 10L);
    }

    @Test
    void projectCannotHaveTwoStartNodes() {
        when(nodeMapper.selectCount(any())).thenReturn(0L, 1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createNode(7L, 10L, nodeRequest("second", "第二起点", "NORMAL", true)));

        assertEquals("CONFLICT", ex.getCode());
    }

    @Test
    void choiceTargetMustBelongToSameProject() {
        when(nodeMapper.selectById(20L)).thenReturn(node(20L, 10L, "NORMAL"));
        when(nodeMapper.selectById(30L)).thenReturn(node(30L, 99L, "NORMAL"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createChoice(7L, 10L, 20L,
                        new StoryGraphDtos.ChoiceRequest(30L, "前往", 0, true)));

        assertEquals("NOT_FOUND", ex.getCode());
    }

    @Test
    void referencedTargetNodeCannotBeDeleted() {
        when(nodeMapper.selectById(20L)).thenReturn(node(20L, 10L, "NORMAL"));
        when(choiceMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.deleteNode(7L, 10L, 20L));

        assertEquals("RESOURCE_IN_USE", ex.getCode());
        verify(nodeMapper, never()).deleteById(20L);
    }

    @Test
    void batchPositionRejectsCrossProjectNodeBeforeAnyUpdate() {
        StoryNode first = node(20L, 10L, "NORMAL");
        StoryNode foreign = node(30L, 99L, "NORMAL");
        when(nodeMapper.selectBatchIds(any())).thenReturn(List.of(first, foreign));
        StoryGraphDtos.BatchPositionsRequest request = new StoryGraphDtos.BatchPositionsRequest(List.of(
                new StoryGraphDtos.PositionItem(20L, new BigDecimal("10"), new BigDecimal("20")),
                new StoryGraphDtos.PositionItem(30L, new BigDecimal("30"), new BigDecimal("40"))));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updatePositions(7L, 10L, request));

        assertEquals("NOT_FOUND", ex.getCode());
        verify(nodeMapper, never()).updateById(any(StoryNode.class));
    }

    @Test
    void batchPositionUpdatesEveryValidatedNode() {
        StoryNode first = node(20L, 10L, "NORMAL");
        StoryNode second = node(30L, 10L, "NORMAL");
        when(nodeMapper.selectBatchIds(any())).thenReturn(List.of(first, second));
        StoryGraphDtos.BatchPositionsRequest request = new StoryGraphDtos.BatchPositionsRequest(List.of(
                new StoryGraphDtos.PositionItem(20L, new BigDecimal("10"), new BigDecimal("20")),
                new StoryGraphDtos.PositionItem(30L, new BigDecimal("30"), new BigDecimal("40"))));

        service.updatePositions(7L, 10L, request);

        assertEquals(new BigDecimal("10"), first.getPositionX());
        assertEquals(new BigDecimal("40"), second.getPositionY());
        verify(nodeMapper).updateById(first);
        verify(nodeMapper).updateById(second);
    }

    @Test
    void endingNodeCannotHaveOutgoingChoice() {
        when(nodeMapper.selectById(20L)).thenReturn(node(20L, 10L, "ENDING"));
        when(nodeMapper.selectById(30L)).thenReturn(node(30L, 10L, "NORMAL"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createChoice(7L, 10L, 20L,
                        new StoryGraphDtos.ChoiceRequest(30L, "继续", 0, true)));

        assertEquals("CONFLICT", ex.getCode());
    }

    private StoryGraphDtos.NodeRequest nodeRequest(String key, String title, String type, boolean start) {
        return new StoryGraphDtos.NodeRequest(key, title, null, type, null, start, null, null);
    }

    private StoryNode node(Long id, Long projectId, String type) {
        StoryNode node = new StoryNode();
        node.setId(id);
        node.setProjectId(projectId);
        node.setNodeType(type);
        return node;
    }
}
