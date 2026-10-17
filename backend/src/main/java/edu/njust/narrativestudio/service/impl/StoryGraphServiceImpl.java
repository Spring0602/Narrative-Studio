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
import java.util.Locale;
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
    private final edu.njust.narrativestudio.service.UnlockReferences references;

    public StoryGraphServiceImpl(StoryNodeMapper nodeMapper, StoryChoiceMapper choiceMapper,
                                 ProjectAccessService accessService,edu.njust.narrativestudio.service.ReleaseService releases,
                                 edu.njust.narrativestudio.service.ProjectMutationGuard guard,
                                 edu.njust.narrativestudio.service.UnlockReferences references) {
        this.nodeMapper = nodeMapper;
        this.choiceMapper = choiceMapper;
        this.accessService = accessService;
        this.releases = releases;
        this.guard=guard;
        this.references=references;
    }

    @Override
    // 【解释】查本项目所有节点与连线，按起点/排序值/ID 排列连线，返回 Graph，不是在后端画图片。
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
    // 【解释】成员可读，requireNode 再验证节点确属本项目。
    public StoryGraphDtos.NodeSummary getNode(Long userId, Long projectId, Long nodeId) {
        accessService.requireMember(userId, projectId);
        return toNodeSummary(requireNode(projectId, nodeId));
    }

    @Override
    @Transactional
    // 【解释】加锁、检查编号唯一、节点上限和起点约束，然后写入节点。
    // 【常见修改】500 节点上限要联查 createNodes 批量入口、ExcelStoryParser、StoryTransferDtos、ReleaseService、IssueService 和坐标批量 DTO。
    // 当前 createNodes 没有复用本方法的总量检查，不能只改这里就宣称所有入口都统一限制了。
    public StoryGraphDtos.NodeSummary createNode(Long userId, Long projectId,
                                                  StoryGraphDtos.NodeRequest request) {
        guard.editor(userId,projectId);
        accessService.requireEditor(userId, projectId);
        validateNodeKeyUnique(projectId, request.nodeKey().trim(), null);
        if (nodeMapper.selectCount(new LambdaQueryWrapper<StoryNode>().eq(StoryNode::getProjectId, projectId)) >= 500)
            throw conflict("单个项目最多允许 500 个节点");
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
    // 【解释】先校验批内编号重复、与已有编号冲突及起点冲突，再逐个写入，事务失败整体回滚。
    // 【修改提示】BatchNodesRequest 的 max=200 是单次批量数量，不是项目累计节点上限；需要总量限制时须在锁内查已有数量再加本次数量。
    public List<StoryGraphDtos.NodeSummary> createNodes(
            Long userId, Long projectId, StoryGraphDtos.BatchNodesRequest request) {
        guard.editor(userId, projectId);
        accessService.requireEditor(userId, projectId);

        Set<String> nodeKeys = new HashSet<>();
        Set<String> normalizedNodeKeys = new HashSet<>();
        long importedStartCount = 0;
        for (StoryGraphDtos.NodeRequest nodeRequest : request.nodes()) {
            String nodeKey = nodeRequest.nodeKey().trim();
            if (!normalizedNodeKeys.add(nodeKey.toLowerCase(Locale.ROOT))) {
                throw conflict("批量导入中存在重复的节点标识：" + nodeKey);
            }
            nodeKeys.add(nodeKey);
            if (Boolean.TRUE.equals(nodeRequest.isStart())) {
                if ("ENDING".equals(nodeRequest.nodeType())) {
                    throw invalid("结局节点不能设为起点：" + nodeKey);
                }
                importedStartCount++;
            }
        }

        if (nodeMapper.selectCount(new LambdaQueryWrapper<StoryNode>()
                .eq(StoryNode::getProjectId, projectId)
                .in(StoryNode::getNodeKey, nodeKeys)) > 0) {
            throw conflict("导入文件包含项目中已存在的节点标识");
        }
        if (importedStartCount > 1 || (importedStartCount == 1 && nodeMapper.selectCount(
                new LambdaQueryWrapper<StoryNode>()
                        .eq(StoryNode::getProjectId, projectId)
                        .eq(StoryNode::getIsStart, true)) > 0)) {
            throw conflict("项目只能有一个起点");
        }

        LocalDateTime now = LocalDateTime.now();
        return request.nodes().stream().map(nodeRequest -> {
            StoryNode node = new StoryNode();
            node.setProjectId(projectId);
            applyNode(node, nodeRequest);
            node.setCreatedAt(now);
            node.setUpdatedAt(now);
            nodeMapper.insert(node);
            return toNodeSummary(node);
        }).toList();
    }

    @Override
    @Transactional
    // 【解释】编号/类型变化需检查高级规则及进度引用；检查唯一性、起点、结局出边后更新。
    // 【修改提示】标题限制看 NodeRequest；不能通过删引用检查解决改名失败，否则跨局解锁标识会失效。
    public StoryGraphDtos.NodeSummary updateNode(Long userId, Long projectId, Long nodeId,
                                                  StoryGraphDtos.NodeRequest request) {
        guard.editor(userId,projectId);
        accessService.requireEditor(userId, projectId);
        StoryNode node = requireNode(projectId, nodeId);
        if(!node.getNodeKey().equals(request.nodeKey()) || !node.getNodeType().equals(request.nodeType()))
            references.requireUnused(projectId,node.getNodeKey(),false);
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
    // 【解释】验证权限、发布版本引用、高级解锁引用和入边后删除；不能简单绕过保护进行强制删除。
    public void deleteNode(Long userId, Long projectId, Long nodeId) {
        guard.editor(userId,projectId);
        accessService.requireEditor(userId, projectId);
        requireNode(projectId, nodeId);
        releases.requireUnpublished(projectId,nodeId,true);
        references.requireUnused(projectId,requireNode(projectId,nodeId).getNodeKey(),false);
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
    // 【解释】一次检查重复 ID、全部节点是否存在且属于本项目，再统一更新时间与坐标。
    // 【修改提示】坐标范围在 NodeRequest 和 PositionItem 两处；限制改变时需同步前端拖动与数据库精度。
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
    // 【解释】查询指定起点的选项，按 sortOrder、ID 升序。
    // 【常见修改】要排序值大的在前，只改 sortOrder 对应 orderByAsc 为 orderByDesc；图查询和模拟展示排序也要核对。
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
    // 【解释】源与目标均须属于项目；源不能是结局；检查 1000 条上限后插入。
    // 【修改提示】扩大连线容量须同步 Excel/通用导入 DTO 等限制，不要混淆节点上限。
    public StoryGraphDtos.ChoiceSummary createChoice(Long userId, Long projectId, Long sourceNodeId,
                                                      StoryGraphDtos.ChoiceRequest request) {
        guard.editor(userId,projectId);
        accessService.requireEditor(userId, projectId);
        StoryNode source = requireNode(projectId, sourceNodeId);
        requireNode(projectId, request.targetNodeId());
        if ("ENDING".equals(source.getNodeType())) throw conflict("结局节点不能创建选择");
        if (choiceMapper.selectCount(new LambdaQueryWrapper<StoryChoice>().eq(StoryChoice::getProjectId, projectId)) >= 1000)
            throw conflict("单个项目最多允许 1000 条选择");
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
    // 【解释】验证源节点、目标节点、选项归属，更新目标与文本等，不允许通过此方法换到其他起点。
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
    // 【解释】验证项目/起点归属并保护已发布引用，再删除选项。
    public void deleteChoice(Long userId, Long projectId, Long sourceNodeId, Long choiceId) {
        guard.editor(userId,projectId);
        accessService.requireEditor(userId, projectId);
        requireNode(projectId, sourceNodeId);
        requireChoice(projectId, sourceNodeId, choiceId);
        releases.requireUnpublished(projectId,choiceId,false);
        choiceMapper.deleteById(choiceId);
    }

    // 【解释】项目内查重，更新时排除自己；不要删排除条件，否则不改编号也会撞到自己。
    private void validateNodeKeyUnique(Long projectId, String nodeKey, Long excludedId) {
        LambdaQueryWrapper<StoryNode> query = new LambdaQueryWrapper<StoryNode>()
                .eq(StoryNode::getProjectId, projectId).eq(StoryNode::getNodeKey, nodeKey);
        if (excludedId != null) query.ne(StoryNode::getId, excludedId);
        if (nodeMapper.selectCount(query) > 0) throw conflict("节点标识在项目内必须唯一");
    }

    // 【解释】仅当请求要设为起点时检查：不能是结局、除自己外不能已有起点；不负责强制编辑中的图始终有起点。
    private void validateStart(Long projectId, Long excludedId, StoryGraphDtos.NodeRequest request) {
        if (!Boolean.TRUE.equals(request.isStart())) return;
        if ("ENDING".equals(request.nodeType())) throw invalid("结局节点不能设为起点");
        LambdaQueryWrapper<StoryNode> query = new LambdaQueryWrapper<StoryNode>()
                .eq(StoryNode::getProjectId, projectId).eq(StoryNode::getIsStart, true);
        if (excludedId != null) query.ne(StoryNode::getId, excludedId);
        if (nodeMapper.selectCount(query) > 0) throw conflict("项目只能有一个起点");
    }

    // 【解释】查询是否存在出边，用于拒绝将有出边节点改成结局。
    private boolean hasOutgoingChoices(Long projectId, Long nodeId) {
        return choiceMapper.selectCount(new LambdaQueryWrapper<StoryChoice>()
                .eq(StoryChoice::getProjectId, projectId)
                .eq(StoryChoice::getSourceNodeId, nodeId)) > 0;
    }

    // 【解释】校验节点存在且归属当前项目。
    private StoryNode requireNode(Long projectId, Long nodeId) {
        StoryNode node = nodeMapper.selectById(nodeId);
        if (node == null || !projectId.equals(node.getProjectId())) throw BusinessException.notFound("剧情节点不存在");
        return node;
    }

    // 【解释】同时核对项目、源节点和选项 ID，防止越权及串线。
    private StoryChoice requireChoice(Long projectId, Long sourceNodeId, Long choiceId) {
        StoryChoice choice = choiceMapper.selectById(choiceId);
        if (choice == null || !projectId.equals(choice.getProjectId())
                || !sourceNodeId.equals(choice.getSourceNodeId())) {
            throw BusinessException.notFound("剧情选择不存在");
        }
        return choice;
    }

    // 【解释】清理文本、设置类型和起点；坐标未传时默认 0。
    // 【常见修改】默认坐标改为 60 可将两处三元式的 ZERO 换成 BigDecimal.valueOf(60)，但会同时影响更新时未传坐标的情况。
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

    // 【解释】填写目标、文本、排序与启用状态；未传排序为 0，未传 enabled 为 true。
    // 【常见修改】默认排序 10：request.sortOrder()==null ? 10 : request.sortOrder()；此处被新增/修改共用，不能当成只改新增。
    private void applyChoice(StoryChoice choice, StoryGraphDtos.ChoiceRequest request) {
        choice.setTargetNodeId(request.targetNodeId());
        choice.setChoiceText(request.choiceText().trim());
        choice.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        choice.setEnabled(request.enabled() == null || request.enabled());
    }

    // 【解释】节点实体转 DTO；新增返回字段需要同步 NodeSummary、前端及其他构造调用。
    private StoryGraphDtos.NodeSummary toNodeSummary(StoryNode node) {
        return new StoryGraphDtos.NodeSummary(node.getId(), node.getProjectId(), node.getNodeKey(), node.getTitle(),
                node.getContent(), node.getNodeType(), node.getScene(), node.getIsStart(), node.getPositionX(),
                node.getPositionY(), node.getUpdatedAt());
    }

    // 【解释】选项实体转 DTO；不返回条件/效果，它们通过规则接口读取。
    private StoryGraphDtos.ChoiceSummary toChoiceSummary(StoryChoice choice) {
        return new StoryGraphDtos.ChoiceSummary(choice.getId(), choice.getProjectId(), choice.getSourceNodeId(),
                choice.getTargetNodeId(), choice.getChoiceText(), choice.getSortOrder(), choice.getEnabled(),
                choice.getUpdatedAt());
    }

    // 【解释】参数非法统一返回 400 业务异常。
    private BusinessException invalid(String message) {
        return new BusinessException("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
    }

    // 【解释】业务状态冲突返回 409，不应统一改成 200。
    private BusinessException conflict(String message) {
        return BusinessException.conflict(message);
    }

    // 【解释】可选文本空白转 null，其余 trim。
    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
