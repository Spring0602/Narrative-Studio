package edu.njust.narrativestudio.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("state_effect")
public class StateEffect {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long choiceId;
    private Long variableId;
    private String operation;
    private String operandValue;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChoiceId() { return choiceId; }
    public void setChoiceId(Long choiceId) { this.choiceId = choiceId; }
    public Long getVariableId() { return variableId; }
    public void setVariableId(Long variableId) { this.variableId = variableId; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getOperandValue() { return operandValue; }
    public void setOperandValue(String operandValue) { this.operandValue = operandValue; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
