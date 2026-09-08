package edu.njust.narrativestudio.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("playtest_step")
public class PlaytestStep {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Integer stepNo;
    private Long nodeId;
    private Long choiceId;
    private String stateBefore;
    private String stateAfter;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Integer getStepNo() { return stepNo; }
    public void setStepNo(Integer stepNo) { this.stepNo = stepNo; }
    public Long getNodeId() { return nodeId; }
    public void setNodeId(Long nodeId) { this.nodeId = nodeId; }
    public Long getChoiceId() { return choiceId; }
    public void setChoiceId(Long choiceId) { this.choiceId = choiceId; }
    public String getStateBefore() { return stateBefore; }
    public void setStateBefore(String stateBefore) { this.stateBefore = stateBefore; }
    public String getStateAfter() { return stateAfter; }
    public void setStateAfter(String stateAfter) { this.stateAfter = stateAfter; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
