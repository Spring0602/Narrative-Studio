package edu.njust.narrativestudio.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@TableName("test_feedback")
// 【持久化模型】对应 test_feedback 表；用户反馈，可关联会话及步骤，reporterId 标记提交者。
// 【修改提示】getter/setter 只读写 Java 对象，新增持久化字段需同步 SQL、DTO/Service 和相关快照/测试；仅展示计算值优先放响应 DTO。
public class TestFeedback {
    @TableId(type=IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long sessionId;
    private Long stepId;
    private Long reporterId;
    private String title;
    private String description;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId=value; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long value) { sessionId=value; }
    public Long getStepId() { return stepId; }
    public void setStepId(Long value) { stepId=value; }
    public Long getReporterId() { return reporterId; }
    public void setReporterId(Long value) { reporterId=value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title=value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description=value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status=value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt=value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt=value; }
}
