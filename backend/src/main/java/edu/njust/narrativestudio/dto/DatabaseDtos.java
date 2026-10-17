package edu.njust.narrativestudio.dto;
import jakarta.validation.constraints.*;
import java.util.List;
// 【扩展请求】角色关系、知识、出场、选项草稿、反馈和状态变更共用此文件，不是数据库建表脚本。
// 【常见修改】反馈标题最多 100：FeedbackRequest.title 的 @Size(max=150) 改为 100；关系类型长度则改 RelationRequest。
// StatusRequest 同时影响反馈和检测问题；新增状态要同步各自 Service、前端和库约束，不能只加正则。
// DraftRequest 文本允许为空，转正时 Service 再要求非空；不要把草稿与正式选项的要求混在一起。
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
