package edu.njust.narrativestudio.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("state_variable")
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
