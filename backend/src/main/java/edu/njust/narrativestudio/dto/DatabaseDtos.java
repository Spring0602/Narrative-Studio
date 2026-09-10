package edu.njust.narrativestudio.dto;
import jakarta.validation.constraints.*;
import java.util.List;
public final class DatabaseDtos {
    private DatabaseDtos() {}
    public record RelationRequest(@NotNull Long sourceCharacterId,@NotNull Long targetCharacterId,
            @NotBlank @Size(max=40) String relationType,@Size(max=1000) String description) {}
    public record KnowledgeRequest(@NotBlank @Pattern(regexp="[A-Za-z][A-Za-z0-9_]{0,99}") String knowledgeKey,
            @NotBlank @Pattern(regexp="UNKNOWN|SUSPECTED|KNOWN") String knowledgeLevel,
            @Size(max=1000) String description,Long acquiredNodeId) {}
    public record CastRequest(@NotNull @Size(max=200) List<@NotNull Long> characterIds) {}
    public record DraftRequest(@NotNull Long sourceNodeId,@Size(max=500) String choiceText,
            @NotNull @Min(0) Integer sortOrder) {}
    public record PromoteRequest(@NotNull Long targetNodeId) {}
    public record FeedbackRequest(Long sessionId,Long stepId,@NotBlank @Size(max=150) String title,
            @NotBlank @Size(max=10000) String description) {}
    public record StatusRequest(@NotBlank @Pattern(regexp="OPEN|RESOLVED|IGNORED") String status) {}
}
