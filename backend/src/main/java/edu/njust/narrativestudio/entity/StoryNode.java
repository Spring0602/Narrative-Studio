package edu.njust.narrativestudio.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("story_node")
public class StoryNode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String nodeKey;
    private String title;
    private String content;
    private String nodeType;
    private String scene;
    private Boolean isStart;
    private BigDecimal positionX;
    private BigDecimal positionY;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getNodeKey() { return nodeKey; }
    public void setNodeKey(String nodeKey) { this.nodeKey = nodeKey; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public Boolean getIsStart() { return isStart; }
    public void setIsStart(Boolean isStart) { this.isStart = isStart; }
    public BigDecimal getPositionX() { return positionX; }
    public void setPositionX(BigDecimal positionX) { this.positionX = positionX; }
    public BigDecimal getPositionY() { return positionY; }
    public void setPositionY(BigDecimal positionY) { this.positionY = positionY; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
