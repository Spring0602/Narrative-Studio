package edu.njust.narrativestudio.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@TableName("story_choice_draft")
// 【持久化模型】对应 story_choice_draft 表；未补全目标的选项草稿，转正时另建 StoryChoice，不直接修改表名。
// 【修改提示】getter/setter 只读写 Java 对象，新增持久化字段需同步 SQL、DTO/Service 和相关快照/测试；仅展示计算值优先放响应 DTO。
public class StoryChoiceDraft {
    @TableId(type=IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long sourceNodeId;
    private String choiceText;
    private Integer sortOrder;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId=value; }
    public Long getSourceNodeId() { return sourceNodeId; }
    public void setSourceNodeId(Long value) { sourceNodeId=value; }
    public String getChoiceText() { return choiceText; }
    public void setChoiceText(String value) { choiceText=value; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer value) { sortOrder=value; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long value) { createdBy=value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt=value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt=value; }
}
