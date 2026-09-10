package edu.njust.narrativestudio.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@TableName("test_feedback")
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
