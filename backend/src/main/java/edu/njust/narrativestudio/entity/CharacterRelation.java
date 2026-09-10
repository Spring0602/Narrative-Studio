package edu.njust.narrativestudio.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@TableName("character_relation")
public class CharacterRelation {
    @TableId(type=IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long sourceCharacterId;
    private Long targetCharacterId;
    private String relationType;
    private String description;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId=value; }
    public Long getSourceCharacterId() { return sourceCharacterId; }
    public void setSourceCharacterId(Long value) { sourceCharacterId=value; }
    public Long getTargetCharacterId() { return targetCharacterId; }
    public void setTargetCharacterId(Long value) { targetCharacterId=value; }
    public String getRelationType() { return relationType; }
    public void setRelationType(String value) { relationType=value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description=value; }
}
