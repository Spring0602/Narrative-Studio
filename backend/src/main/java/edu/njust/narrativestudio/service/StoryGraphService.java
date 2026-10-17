package edu.njust.narrativestudio.service;

import edu.njust.narrativestudio.dto.StoryGraphDtos;
import java.util.List;

// 【业务契约】声明 StoryGraphService 的可调用方法，具体逻辑在 impl/StoryGraphServiceImpl.java；返回类型和参数是调用约定。
// 【修改提示】添加查询参数须同步 Controller、接口、实现类与测试调用；只改排序/提示/DTO 内部字段通常不用改接口签名。
public interface StoryGraphService {
    StoryGraphDtos.Graph getGraph(Long userId, Long projectId);
    StoryGraphDtos.NodeSummary getNode(Long userId, Long projectId, Long nodeId);
    StoryGraphDtos.NodeSummary createNode(Long userId, Long projectId, StoryGraphDtos.NodeRequest request);
    List<StoryGraphDtos.NodeSummary> createNodes(Long userId, Long projectId,
                                                 StoryGraphDtos.BatchNodesRequest request);
    StoryGraphDtos.NodeSummary updateNode(Long userId, Long projectId, Long nodeId,
                                          StoryGraphDtos.NodeRequest request);
    void deleteNode(Long userId, Long projectId, Long nodeId);
    void updatePositions(Long userId, Long projectId, StoryGraphDtos.BatchPositionsRequest request);
    List<StoryGraphDtos.ChoiceSummary> listChoices(Long userId, Long projectId, Long sourceNodeId);
    StoryGraphDtos.ChoiceSummary createChoice(Long userId, Long projectId, Long sourceNodeId,
                                              StoryGraphDtos.ChoiceRequest request);
    StoryGraphDtos.ChoiceSummary updateChoice(Long userId, Long projectId, Long sourceNodeId,
                                              Long choiceId, StoryGraphDtos.ChoiceRequest request);
    void deleteChoice(Long userId, Long projectId, Long sourceNodeId, Long choiceId);
}
