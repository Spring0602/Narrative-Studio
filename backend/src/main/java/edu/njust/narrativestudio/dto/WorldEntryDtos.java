package edu.njust.narrativestudio.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class WorldEntryDtos {
    private WorldEntryDtos() {}

    // 答辩修改点：SaveRequest 是新增、修改共用的请求格式，改这里会同时影响两个接口。
    // record 自动生成访问方法，例如 request.title()，不是 getTitle()，也没有 setter。
    public record SaveRequest(
            // 【增加类型】例如增加“事件”：把下面的正则改为
            // @NotBlank @Pattern(regexp = "SETTING|LOCATION|FACTION|HISTORY|RULE|OTHER|EVENT") String entryType,
            // 同步前端类型选项、显示名称和测试；数据库 entry_type 是字符串列，不是 Java 枚举。
            @NotBlank @Pattern(regexp = "SETTING|LOCATION|FACTION|HISTORY|RULE|OTHER") String entryType,
            // 【标题最多 50 字符】替换下一行为：
            // @NotBlank @Size(max = 50, message = "标题不能超过50个字符") String title,
            // 缩小限制一般不需要改原 VARCHAR(100)；扩大到超过列容量时必须同步数据库。
            // Controller 的 @Valid 触发这里的校验；普通 Java 调用 Service 不会自动触发它。
            @NotBlank @Size(max = 100) String title,
            // 【调整正文长度】修改 max；@NotBlank 拒绝 null、空串和纯空白。
            // 若要允许正文不填，不能只删除 @NotBlank：Service 中 content().trim() 也要处理 null。
            @NotBlank @Size(max = 10000) String content,
            // 【排序必填】@Min(0) 只限制非 null 的值 >= 0，不会要求必填。
            // 要求必填可改成（这里用完整类名，免得漏加 import）：
            // @jakarta.validation.constraints.NotNull(message = "排序值不能为空") @Min(0) Integer sortOrder
            // 【设置上限】可再加 @jakarta.validation.constraints.Max(9999)。
            // 默认值仍由 Service 的 apply 方法设置，@Min 不负责赋默认值。
            @Min(0) Integer sortOrder) {}

    // 【返回创建时间】在 updatedAt 后追加：LocalDateTime createdAt。
    // 同时在 ServiceImpl.toSummary 中 new Summary(...) 的末尾追加 entry.getCreatedAt()，
    // 并检查其他构造 Summary 的位置；record 构造参数数量、顺序必须一致。
    // Entity 和数据库已有 createdAt/created_at，此项不需要新增数据库字段。
    public record Summary(Long id, Long projectId, String entryType, String title,
                          String content, Integer sortOrder, LocalDateTime updatedAt) {}
}
