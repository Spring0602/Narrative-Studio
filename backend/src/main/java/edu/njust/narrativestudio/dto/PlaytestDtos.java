package edu.njust.narrativestudio.dto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class PlaytestDtos {
    private PlaytestDtos() {}
    public record AdvanceRequest(@NotNull @Min(0) Integer expectedStepNo) {}
    public record NodeView(Long id, String nodeKey, String title, String content, String nodeType) {}
    public record ChoiceView(Long id, Long targetNodeId, String choiceText, Integer sortOrder) {}
    public record SessionView(Long id, Long projectId, Long testerId, String status, NodeView currentNode,
            int stepNo, Map<String,String> state, List<ChoiceView> availableChoices,
            boolean deadEnd, LocalDateTime startedAt, LocalDateTime finishedAt) {}
    public record StepView(Long id, int stepNo, Long nodeId, Long choiceId,
            Map<String,String> stateBefore, Map<String,String> stateAfter, LocalDateTime createdAt) {}
}
