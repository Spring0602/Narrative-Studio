package edu.njust.narrativestudio.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.njust.narrativestudio.dto.StoryGraphDtos;
import edu.njust.narrativestudio.entity.StoryChoice;
import edu.njust.narrativestudio.entity.StoryNode;
import edu.njust.narrativestudio.exception.BusinessException;
import edu.njust.narrativestudio.mapper.StoryChoiceMapper;
import edu.njust.narrativestudio.mapper.StoryNodeMapper;
import edu.njust.narrativestudio.service.ProjectAccessService;
import edu.njust.narrativestudio.service.StoryGraphService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoryGraphServiceImpl implements StoryGraphService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final StoryNodeMapper nodeMapper;
    private final StoryChoiceMapper choiceMapper;
    private final ProjectAccessService accessService;
    private final edu.njust.narrativestudio.service.ReleaseService releases;
    private final edu.njust.narrativestudio.service.ProjectMutationGuard guard;

    public StoryGraphServiceImpl(StoryNodeMapper nodeMapper, StoryChoiceMapper choiceMapper,
                                 ProjectAccessService accessService,edu.njust.narrativestudio.service.ReleaseService releases,
                                 edu.njust.narrativestudio.service.ProjectMutationGuard guard) {
        this.nodeMapper = nodeMapper;
        this.choiceMapper = choiceMapper;
        this.accessService = accessService;
        this.releases = releases;
        this.guard=guard;
    }

    @Override
    public StoryGraphDtos.Graph getGraph(Long userId, Long projectId) {
        accessService.requireMember(userId, projectId);
        List<StoryNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<StoryNode>()
                .eq(StoryNode::getProjectId, projectId).orderByAsc(StoryNode::getId));
        List<StoryChoice> choices = choiceMapper.selectList(new LambdaQueryWrapper<StoryChoice>()
                .eq(StoryChoice::getProjectId, projectId)
                .orderByAsc(StoryChoice::getSourceNodeId)
                .orderByAsc(StoryChoice::getSortOrder)
                .orderByAsc(StoryChoice::getId));
        return new StoryGraphDtos.Graph(nodes.stream().map(this::toNodeSummary).toList(),
                choices.stream().map(this::toChoiceSummary).toList());
    }

    @Override
    public StoryGraphDtos.NodeSummary getNode(Long userId, Long projectId, Long nodeId) {
        accessService.requireMember(userId, projectId);
        return toNodeSummary(requireNode(projectId, nodeId));
    }

    @Override
    @Transactional
    public StoryGraphDtos.NodeSummary createNode(Long userId, Long projectId,
                                                  StoryGraphDtos.NodeRequest request) {
        guard.editor(userId,projectId);
        accessService.requireEditor(userId, projectId);
        validateNodeKeyUnique(projectId, request.nodeKey().trim(), null);
        validateStart(projectId, null, request);
        LocalDateTime now = LocalDateTime.now();
        StoryNode node = new StoryNode();
        node.setProjectId(projectId);
        applyNode(node, request);
        node.setCreatedAt(now);
        node.setUpdatedAt(now);
        nodeMapper.insert(node);
        return toNodeSummary(node);
    }

    @Override
    @Transactional
    public StoryGraphDtos.NodeSummary updateNode(Long userId, Long projectId, Long nodeId,
                                                  StoryGraphDtos.NodeRequest request) {
        guard.editor(userId,projectId);
        accessService.requireEditor(userId, projectId);
        StoryNode node = requireNode(projectId, nodeId);
        validateNodeKeyUnique(projectId, request.nodeKey().trim(), nodeId);
        validateStart(projectId, nodeId, request);
        if ("ENDING".equals(request.nodeType()) && hasOutgoingChoices(projectId, nodeId)) {
            throw conflict("存在出边的节点不能改为结局节点");
        }
        applyNode(node, request);
        node.setUpdatedAt(LocalDateTime.now());
        nodeMapper.updateById(node);
        return toNodeSummary(node);
    }

    @Override
    @Transactional
    public void deleteNode(Long userId, Long projectId, Long nodeId) {
        guard.editor(userId,projectId);
        accessService.requireEditor(userId, projectId);
        requireNode(projectId, nodeId);
        releases.requireUnpublished(projectId,nodeId,true);
        Long incoming = choiceMapper.selectCount(new LambdaQueryWrapper<StoryChoice>()
                .eq(StoryChoice::getProjectId, projectId)
                .eq(StoryChoice::getTargetNodeId, nodeId));
        if (incoming > 0) {
            throw new BusinessException("RESOURCE_IN_USE", "节点仍被其他选择引用，不能删除", HttpStatus.CONFLICT);
        }
        nodeMapper.deleteById(nodeId);
    }

    @Override
    @Transactional
    public void updatePositions(Long userId, Long projectId,
                                StoryGraphDtos.BatchPositionsRequest request) {
        guard.editor(userId,projectId);
        accessService.requireEditor(userId, projectId);
        Set<Long> ids = new HashSet<>();
        for (StoryGraphDtos.PositionItem item : request.positions()) {
            if (!ids.add(item.nodeId())) throw invalid("批量坐标中存在重复节点");
        }
        Map<Long, StoryNode> nodes = nodeMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(StoryNode::getId, Function.identity()));
        if (nodes.size() != ids.size() || nodes.values().stream()
                .anyMatch(node -> !projectId.equals(node.getProjectId()))) {
            throw BusinessException.notFound("批量坐标中包含不存在的项目节点");
        }
        LocalDateTime now = LocalDateTime.now();
        for (StoryGraphDtos.PositionItem item : request.positions()) {
            StoryNode node = nodes.get(item.nodeId());
            node.setPositionX(item.positionX());
            node.setPositionY(item.positionY());
            node.setUpdatedAt(now);
            nodeMapper.updateById(node);
        }
    }

    @Override
    public List<StoryGraphDtos.ChoiceSummary> listChoices(Long userId, Long projectId, Long sourceNodeId) {
        accessService.requireMember(userId, projectId);
        requireNode(projectId, sourceNodeId);
        return choiceMapper.selectList(new LambdaQueryWrapper<StoryChoice>()
                        .eq(StoryChoice::getProjectId, projectId)
                        .eq(StoryChoice::getSourceNodeId, sourceNodeId)
                        .orderByAsc(StoryChoice::getSortOrder)
                        .orderByAsc(StoryChoice::getId))
                .stream().map(this::toChoiceSummary).toList();
    }

    @Override
    @Transactional
    public StoryGraphDtos.ChoiceSummary createChoice(Long userId, Long projectId, Long sourceNodeId,
                                                      StoryGraphDtos.ChoiceRequest request) {
        guard.editor(userId,projectId);
        accessService.requireEditor(userId, projectId);
        StoryNode source = requireNode(projectId, sourceNodeId);
        requireNode(projectId, request.targetNodeId());
        if ("ENDING".equals(source.getNodeType())) throw conflict("结局节点不能创建选择");
        LocalDateTime now = LocalDateTime.now();
        StoryChoice choice = new StoryChoice();
        choice.setProjectId(projectId);
        choice.setSourceNodeId(sourceNodeId);
        applyChoice(choice, request);
        choice.setCreatedAt(now);
        choice.setUpdatedAt(now);
        choiceMapper.insert(choice);
        return toChoiceSummary(choice);
    }

    @Override
    @Transactional
    public StoryGraphDtos.ChoiceSummary updateChoice(Long userId, Long projectId, Long sourceNodeId,
                                                      Long choiceId, StoryGraphDtos.ChoiceRequest request) {
        guard.editor(userId,projectId);
        accessService.requireEditor(userId, projectId);
        StoryNode source = requireNode(projectId, sourceNodeId);
        if ("ENDING".equals(source.getNodeType())) throw conflict("结局节点不能包含选择");
        requireNode(projectId, request.targetNodeId());
        StoryChoice choice = requireChoice(projectId, sourceNodeId, choiceId);
        applyChoice(choice, request);
        choice.setUpdatedAt(LocalDateTime.now());
        choiceMapper.updateById(choice);
        return toChoiceSummary(choice);
    }

    @Override
    @Transactional
    public void deleteChoice(Long userId, Long projectId, Long sourceNodeId, Long choiceId) {
        guard.editor(userId,projectId);
        accessService.requireEditor(userId, projectId);
        requireNode(projectId, sourceNodeId);
        requireChoice(projectId, sourceNodeId, choiceId);
        releases.requireUnpublished(projectId,choiceId,false);
        choiceMapper.deleteById(choiceId);
    }

    private void validateNodeKeyUnique(Long projectId, String nodeKey, Long excludedId) {
        LambdaQueryWrapper<StoryNode> query = new LambdaQueryWrapper<StoryNode>()
                .eq(StoryNode::getProjectId, projectId).eq(StoryNode::getNodeKey, nodeKey);
        if (excludedId != null) query.ne(StoryNode::getId, excludedId);
        if (nodeMapper.selectCount(query) > 0) throw conflict("节点标识在项目内必须唯一");
    }

    private void validateStart(Long projectId, Long excludedId, StoryGraphDtos.NodeRequest request) {
        if (!Boolean.TRUE.equals(request.isStart())) return;
        if ("ENDING".equals(request.nodeType())) throw invalid("结局节点不能设为起点");
        LambdaQueryWrapper<StoryNode> query = new LambdaQueryWrapper<StoryNode>()
                .eq(StoryNode::getProjectId, projectId).eq(StoryNode::getIsStart, true);
        if (excludedId != null) query.ne(StoryNode::getId, excludedId);
        if (nodeMapper.selectCount(query) > 0) throw conflict("项目只能有一个起点");
    }

    private boolean hasOutgoingChoices(Long projectId, Long nodeId) {
        return choiceMapper.selectCount(new LambdaQueryWrapper<StoryChoice>()
                .eq(StoryChoice::getProjectId, projectId)
                .eq(StoryChoice::getSourceNodeId, nodeId)) > 0;
    }

    private StoryNode requireNode(Long projectId, Long nodeId) {
        StoryNode node = nodeMapper.selectById(nodeId);
        if (node == null || !projectId.equals(node.getProjectId())) throw BusinessException.notFound("剧情节点不存在");
        return node;
    }

    private StoryChoice requireChoice(Long projectId, Long sourceNodeId, Long choiceId) {
        StoryChoice choice = choiceMapper.selectById(choiceId);
        if (choice == null || !projectId.equals(choice.getProjectId())
                || !sourceNodeId.equals(choice.getSourceNodeId())) {
            throw BusinessException.notFound("剧情选择不存在");
        }
        return choice;
    }

    private void applyNode(StoryNode node, StoryGraphDtos.NodeRequest request) {
        node.setNodeKey(request.nodeKey().trim());
        node.setTitle(request.title().trim());
        node.setContent(trimToNull(request.content()));
        node.setNodeType(request.nodeType());
        node.setScene(trimToNull(request.scene()));
        node.setIsStart(Boolean.TRUE.equals(request.isStart()));
        node.setPositionX(request.positionX() == null ? ZERO : request.positionX());
        node.setPositionY(request.positionY() == null ? ZERO : request.positionY());
    }

    private void applyChoice(StoryChoice choice, StoryGraphDtos.ChoiceRequest request) {
        choice.setTargetNodeId(request.targetNodeId());
        choice.setChoiceText(request.choiceText().trim());
        choice.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        choice.setEnabled(request.enabled() == null || request.enabled());
    }

    private StoryGraphDtos.NodeSummary toNodeSummary(StoryNode node) {
        return new StoryGraphDtos.NodeSummary(node.getId(), node.getProjectId(), node.getNodeKey(), node.getTitle(),
                node.getContent(), node.getNodeType(), node.getScene(), node.getIsStart(), node.getPositionX(),
                node.getPositionY(), node.getUpdatedAt());
    }

    private StoryGraphDtos.ChoiceSummary toChoiceSummary(StoryChoice choice) {
        return new StoryGraphDtos.ChoiceSummary(choice.getId(), choice.getProjectId(), choice.getSourceNodeId(),
                choice.getTargetNodeId(), choice.getChoiceText(), choice.getSortOrder(), choice.getEnabled(),
                choice.getUpdatedAt());
    }

    private BusinessException invalid(String message) {
        return new BusinessException("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
    }

    private BusinessException conflict(String message) {
        return BusinessException.conflict(message);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
