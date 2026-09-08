package edu.njust.narrativestudio.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("choice_condition")
public class ChoiceCondition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long choiceId;
    private Long variableId;
    private String operator;
    private String expectedValue;
    private Integer conditionGroup;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChoiceId() { return choiceId; }
    public void setChoiceId(Long choiceId) { this.choiceId = choiceId; }
    public Long getVariableId() { return variableId; }
    public void setVariableId(Long variableId) { this.variableId = variableId; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getExpectedValue() { return expectedValue; }
    public void setExpectedValue(String expectedValue) { this.expectedValue = expectedValue; }
    public Integer getConditionGroup() { return conditionGroup; }
    public void setConditionGroup(Integer conditionGroup) { this.conditionGroup = conditionGroup; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
