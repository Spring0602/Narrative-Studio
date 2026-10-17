package edu.njust.narrativestudio.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

// 【规则格式】VariableRequest 定义变量；ConditionInput/EffectInput 定义判断和变化；RulesRequest 是整组选项规则替换。
// 默认 persistenceScope 为 SESSION，PROFILE 才跨局；新增类型/操作符还需改引擎与前端，不能只扩大正则。
// 【常见修改】每组选项最多 50 条普通条件：conditions 的 @Size(max=100) 改为 50；effects 上限独立，通用导入也要检查。
// conditionGroup 是 AND/OR 分组编号，不是排序值，改变分组会改变能否选择。
public final class RuleDtos {
    private RuleDtos() {}
    public record VariableRequest(
            @NotBlank @Size(max=64) @Pattern(regexp="[A-Za-z][A-Za-z0-9_]*") String variableKey,
            @NotBlank @Size(max=100) String displayName,
            @NotNull @Pattern(regexp="BOOLEAN|INTEGER|STRING") String valueType,
            @NotNull @Size(max=500) String initialValue,
            @Size(max=500) String description,
            @Pattern(regexp="SESSION|PROFILE") String persistenceScope) {
        public VariableRequest(String key,String name,String type,String initial,String description) {this(key,name,type,initial,description,"SESSION");}
        public VariableRequest {if(persistenceScope==null) persistenceScope="SESSION";}
    }
    public record VariableView(Long id, Long projectId, String variableKey, String displayName,
                               String valueType, String initialValue, String description,String persistenceScope) {}
    public record ConditionInput(@NotNull @Positive Long variableId,
            @NotNull @Pattern(regexp="EQ|NE|GT|GTE|LT|LTE") String operator,
            @NotNull @Size(max=500) String expectedValue,
            @NotNull @Min(0) @Max(100) Integer conditionGroup) {}
    public record EffectInput(@NotNull @Positive Long variableId,
            @NotNull @Pattern(regexp="SET|ADD|SUBTRACT") String operation,
            @NotNull @Size(max=500) String operandValue) {}
    public record RulesRequest(@NotNull @Size(max=100) List<@NotNull @Valid ConditionInput> conditions,
                               @NotNull @Size(max=100) List<@NotNull @Valid EffectInput> effects,UnlockRule unlockRule) {
        public RulesRequest(List<ConditionInput> conditions,List<EffectInput> effects) {this(conditions,effects,null);}
    }
    public record ConditionView(Long id, Long variableId, String operator, String expectedValue, Integer conditionGroup, Integer sortOrder) {}
    public record EffectView(Long id, Long variableId, String operation, String operandValue, Integer sortOrder) {}
    public record RulesView(Long choiceId, List<ConditionView> conditions, List<EffectView> effects,UnlockRule unlockRule) {}
}
