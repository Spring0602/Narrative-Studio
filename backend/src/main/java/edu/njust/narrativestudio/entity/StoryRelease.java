package edu.njust.narrativestudio.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@TableName("story_release")
// 【持久化模型】对应 story_release 表；不可变发布内容的快照及版本号；schemaVersion 是格式版本，不是发布次数。
// 【修改提示】getter/setter 只读写 Java 对象，新增持久化字段需同步 SQL、DTO/Service 和相关快照/测试；仅展示计算值优先放响应 DTO。
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
