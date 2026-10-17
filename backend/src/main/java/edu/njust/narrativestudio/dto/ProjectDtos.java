package edu.njust.narrativestudio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

// 【项目格式】CreateRequest/UpdateRequest 是不同请求类型；Summary 是项目概要。
// 【常见修改】项目名最多 50：两个请求 name 的 @Size(max=100) 都改为 50，核对导入/恢复等入口和前端。
// 返回 createdAt 要在 Summary 追加参数，并同步 ProjectServiceImpl.toSummary 等构造调用；已有字段不必新增数据库列。
public final class ProjectDtos {
    private ProjectDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 1000) String description) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 1000) String description) {}

    public record Summary(Long id, String name, String description, Long ownerId,
                          String status, String memberRole, LocalDateTime updatedAt) {}
}
