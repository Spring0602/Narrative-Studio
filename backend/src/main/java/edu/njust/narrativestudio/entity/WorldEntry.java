package edu.njust.narrativestudio.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

// 答辩修改点：@TableName 指定数据库表名；字段通过项目的驼峰映射对应列，
// 例如 projectId 对应 project_id、sortOrder 对应 sort_order。
// 只修改 Java 字段不会自动给已有 MySQL 表增加列。
@TableName("world_entry")
public class WorldEntry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String entryType;
    private String title;
    private String content;
    // 【增加可选的“简短说明”字段】下面是需要添加的实体代码示例，不是当前已有功能：
    // private String description;
    // public String getDescription() { return description; }
    // public void setDescription(String description) { this.description = description; }
    // 配套 MySQL 增量 SQL 示例（先备份、确认目标库并在测试库验证，不要重复执行）：
    // ALTER TABLE world_entry ADD COLUMN description VARCHAR(200) NULL;
    // 还需要一起修改：
    // 1. SaveRequest 增加 @Size(max = 200) String description；
    // 2. ServiceImpl.apply 增加 entry.setDescription(request.description());
    // 3. Summary 增加 String description，toSummary 构造参数对应追加 entry.getDescription()；
    // 4. 前端表单、请求、显示以及接口测试；检查导入导出、历史快照和恢复是否也需保留此字段。
    // 已有数据库请使用增量迁移，不要靠删表重建来增加字段；测试数据库结构也要同步。
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 【临时计算值】若字段不需要保存到数据库，优先只放在响应 DTO 中。
    // 必须放进 Entity 时可用 @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    // 标记，例如在 private String displayName; 上加它，避免 Mapper 查询不存在的列。
    // getter 读取当前 Java 对象，setter 修改当前 Java 对象；
    // setTitle(...) 本身不会执行 SQL，需要 Service 调用 insert/updateById 才会持久化。
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
