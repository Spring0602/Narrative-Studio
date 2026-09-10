package edu.njust.narrativestudio.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@TableName("character_knowledge")
public class CharacterKnowledge {
    @TableId(type=IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long characterId;
    private String knowledgeKey;
    private String knowledgeLevel;
    private String description;
    private Long acquiredNodeId;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId=value; }
    public Long getCharacterId() { return characterId; }
    public void setCharacterId(Long value) { characterId=value; }
    public String getKnowledgeKey() { return knowledgeKey; }
    public void setKnowledgeKey(String value) { knowledgeKey=value; }
    public String getKnowledgeLevel() { return knowledgeLevel; }
    public void setKnowledgeLevel(String value) { knowledgeLevel=value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description=value; }
    public Long getAcquiredNodeId() { return acquiredNodeId; }
    public void setAcquiredNodeId(Long value) { acquiredNodeId=value; }
}
