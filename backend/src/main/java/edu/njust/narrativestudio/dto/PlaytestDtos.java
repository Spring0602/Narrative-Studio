package edu.njust.narrativestudio.dto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class PlaytestDtos {
    private PlaytestDtos() {}
    // 答辩修改点：例如把下一行替换为自定义校验提示（不改变规则）：
    // public record AdvanceRequest(
    //         @NotNull(message="当前步数不能为空") @Min(value=0, message="当前步数不能为负数") Integer expectedStepNo) {}
    // 第 0 步是真实存在的初始化步骤，不能为了“从 1 开始显示”就把 @Min(0) 改成 @Min(1)。
    // 最大推进步数由 ServiceImpl.advance 控制，不要只在 DTO 加 @Max 就认为业务限制完成了。
    public record AdvanceRequest(@NotNull @Min(0) Integer expectedStepNo) {}
    public record NodeView(Long id, String nodeKey, String title, String content, String nodeType) {}
    public record ChoiceView(Long id, Long targetNodeId, String choiceText, Integer sortOrder) {}
    public record LockedChoiceView(Long id,String choiceText,String reason) {}
    // 【常见修改：返回可选项数量】在 SessionView 参数列表末尾追加 int availableChoiceCount，
    // 然后在 ServiceImpl.view() 的 new SessionView(...) 末尾追加 visible.size()。
    // 同步所有构造调用、前端类型/显示、响应测试；这是计算字段，不需要新增数据库列。
    // 修改 JSON 字段名会影响前端；status 的 RUNNING/COMPLETED/ABORTED 不应直接改成中文。
    public record SessionView(Long id, Long projectId, Long testerId, String status, NodeView currentNode,
            int stepNo, Map<String,String> state, List<ChoiceView> availableChoices,
            boolean deadEnd, LocalDateTime startedAt, LocalDateTime finishedAt,
            Long releaseId, Map<String,String> knowledge,ProgressSnapshot progress,List<LockedChoiceView> lockedChoices) {}
    public record StepView(Long id, int stepNo, Long nodeId, Long choiceId,
            Map<String,String> stateBefore, Map<String,String> stateAfter, LocalDateTime createdAt,
            Map<String,String> knowledgeBefore, Map<String,String> knowledgeAfter,
            ProgressSnapshot progressBefore,ProgressSnapshot progressAfter) {}
}
