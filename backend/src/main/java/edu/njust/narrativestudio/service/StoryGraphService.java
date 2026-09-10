package edu.njust.narrativestudio.service;

import edu.njust.narrativestudio.dto.StoryGraphDtos;
import java.util.List;

public interface StoryGraphService {
    StoryGraphDtos.Graph getGraph(Long userId, Long projectId);
    StoryGraphDtos.NodeSummary getNode(Long userId, Long projectId, Long nodeId);
    StoryGraphDtos.NodeSummary createNode(Long userId, Long projectId, StoryGraphDtos.NodeRequest request);
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
