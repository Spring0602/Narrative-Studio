package edu.njust.narrativestudio.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@TableName("character_relation")
// 【持久化模型】对应 character_relation 表；有方向的角色关系，源/目标都指角色 ID。
// 【修改提示】getter/setter 只读写 Java 对象，新增持久化字段需同步 SQL、DTO/Service 和相关快照/测试；仅展示计算值优先放响应 DTO。
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
