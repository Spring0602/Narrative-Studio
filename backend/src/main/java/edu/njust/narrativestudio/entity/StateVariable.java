package edu.njust.narrativestudio.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("state_variable")
// 【持久化模型】对应 state_variable 表；变量定义和初始值，persistenceScope 区分 SESSION/PROFILE，不存每一步动态值。
// 【修改提示】getter/setter 只读写 Java 对象，新增持久化字段需同步 SQL、DTO/Service 和相关快照/测试；仅展示计算值优先放响应 DTO。
public class StateVariable {
    private String persistenceScope = "SESSION";
    public String getPersistenceScope() {return persistenceScope==null?"SESSION":persistenceScope;}
    public void setPersistenceScope(String value) {persistenceScope=value;}
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String variableKey;
    private String displayName;
    private String valueType;
    private String initialValue;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String description;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getVariableKey() { return variableKey; }
    public void setVariableKey(String variableKey) { this.variableKey = variableKey; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getValueType() { return valueType; }
    public void setValueType(String valueType) { this.valueType = valueType; }
    public String getInitialValue() { return initialValue; }
    public void setInitialValue(String initialValue) { this.initialValue = initialValue; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
