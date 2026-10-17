package edu.njust.narrativestudio.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@TableName("detected_issue")
// 【持久化模型】对应 detected_issue 表；自动检测结果与处理状态；severity 是严重程度，status 是处理进度。
// 【修改提示】getter/setter 只读写 Java 对象，新增持久化字段需同步 SQL、DTO/Service 和相关快照/测试；仅展示计算值优先放响应 DTO。
public class DetectedIssue {
    @TableId(type=IdType.AUTO)
    private Long id;
    private Long projectId;
    private String issueType;
    private String severity;
    private String targetType;
    private Long targetId;
    private String message;
    private String status;
    private LocalDateTime detectedAt;
    private LocalDateTime resolvedAt;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId=value; }
    public String getIssueType() { return issueType; }
    public void setIssueType(String value) { issueType=value; }
    public String getSeverity() { return severity; }
    public void setSeverity(String value) { severity=value; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String value) { targetType=value; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long value) { targetId=value; }
    public String getMessage() { return message; }
    public void setMessage(String value) { message=value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status=value; }
    public LocalDateTime getDetectedAt() { return detectedAt; }
    public void setDetectedAt(LocalDateTime value) { detectedAt=value; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime value) { resolvedAt=value; }
}
