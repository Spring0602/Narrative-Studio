package edu.njust.narrativestudio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

// 【角色格式】SaveRequest 被新增和修改共用；Summary 是响应，含状态和更新时间。
// 【常见修改】名字最多 30：name 的 @Size(max=80) 改为 30，保留 @NotBlank；缩小限制无需扩大数据库列。
// 可选文本字段允许 null，若改必填，前端表单和历史数据编辑流程也需验证。
public final class CharacterDtos {
    private CharacterDtos() {}

    public record SaveRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 500) String summary,
            @Size(max = 5000) String personality,
            @Size(max = 5000) String goal,
            @Size(max = 5000) String valueOrder) {}

    public record Summary(Long id, Long projectId, String name, String summary,
                          String personality, String goal, String valueOrder,
                          String status, LocalDateTime updatedAt) {}
}
