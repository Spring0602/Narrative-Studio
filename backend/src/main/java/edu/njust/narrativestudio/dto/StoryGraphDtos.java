package edu.njust.narrativestudio.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class StoryGraphDtos {
    private StoryGraphDtos() {}

    public record NodeRequest(
            @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String nodeKey,
            @NotBlank @Size(max = 120) String title,
            @Size(max = 10000) String content,
            @NotBlank @Pattern(regexp = "NORMAL|ENDING") String nodeType,
            @Size(max = 100) String scene,
            Boolean isStart,
            @DecimalMin("-99999999.99") @DecimalMax("99999999.99") BigDecimal positionX,
            @DecimalMin("-99999999.99") @DecimalMax("99999999.99") BigDecimal positionY) {}

    public record NodeSummary(Long id, Long projectId, String nodeKey, String title, String content,
                              String nodeType, String scene, Boolean isStart, BigDecimal positionX,
                              BigDecimal positionY, LocalDateTime updatedAt) {}

    public record ChoiceRequest(
            @NotNull Long targetNodeId,
            @NotBlank @Size(max = 500) String choiceText,
            Integer sortOrder,
            Boolean enabled) {}

    public record ChoiceSummary(Long id, Long projectId, Long sourceNodeId, Long targetNodeId,
                                String choiceText, Integer sortOrder, Boolean enabled,
                                LocalDateTime updatedAt) {}

    public record PositionItem(
            @NotNull Long nodeId,
            @NotNull @DecimalMin("-99999999.99") @DecimalMax("99999999.99") BigDecimal positionX,
            @NotNull @DecimalMin("-99999999.99") @DecimalMax("99999999.99") BigDecimal positionY) {}

    public record BatchPositionsRequest(
            @NotEmpty @Size(max = 500) List<@Valid PositionItem> positions) {}

    public record BatchNodesRequest(
            @NotEmpty @Size(max = 200) List<@Valid NodeRequest> nodes) {}

    public record Graph(List<NodeSummary> nodes, List<ChoiceSummary> choices) {}
}
