package edu.njust.narrativestudio.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@TableName("story_release")
public class StoryRelease {
    @TableId(type=IdType.AUTO)
    private Long id;
    private Long projectId;
    private Integer versionNo;
    private Long publishedBy;
    private LocalDateTime publishedAt;
    private Integer schemaVersion;
    private String contentSnapshot;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId=value; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer value) { versionNo=value; }
    public Long getPublishedBy() { return publishedBy; }
    public void setPublishedBy(Long value) { publishedBy=value; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime value) { publishedAt=value; }
    public Integer getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(Integer value) { schemaVersion=value; }
    public String getContentSnapshot() { return contentSnapshot; }
    public void setContentSnapshot(String value) { contentSnapshot=value; }
}
