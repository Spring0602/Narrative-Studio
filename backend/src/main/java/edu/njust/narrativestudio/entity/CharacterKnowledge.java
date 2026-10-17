package edu.njust.narrativestudio.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@TableName("character_knowledge")
// 【持久化模型】对应 character_knowledge 表；角色知识定义，acquiredNodeId 为空表示初始可获得；不同于模拟知识快照。
// 【修改提示】getter/setter 只读写 Java 对象，新增持久化字段需同步 SQL、DTO/Service 和相关快照/测试；仅展示计算值优先放响应 DTO。
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
