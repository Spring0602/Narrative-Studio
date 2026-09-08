package edu.njust.narrativestudio.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class RuleDtos {
    private RuleDtos() {}
    public record VariableRequest(
            @NotBlank @Size(max=64) @Pattern(regexp="[A-Za-z][A-Za-z0-9_]*") String variableKey,
            @NotBlank @Size(max=100) String displayName,
            @NotNull @Pattern(regexp="BOOLEAN|INTEGER|STRING") String valueType,
            @NotNull @Size(max=500) String initialValue,
            @Size(max=500) String description) {}
    public record VariableView(Long id, Long projectId, String variableKey, String displayName,
                               String valueType, String initialValue, String description) {}
    public record ConditionInput(@NotNull @Positive Long variableId,
            @NotNull @Pattern(regexp="EQ|NE|GT|GTE|LT|LTE") String operator,
            @NotNull @Size(max=500) String expectedValue,
            @NotNull @Min(0) @Max(100) Integer conditionGroup) {}
    public record EffectInput(@NotNull @Positive Long variableId,
            @NotNull @Pattern(regexp="SET|ADD|SUBTRACT") String operation,
            @NotNull @Size(max=500) String operandValue) {}
    public record RulesRequest(@NotNull @Size(max=100) List<@NotNull @Valid ConditionInput> conditions,
                               @NotNull @Size(max=100) List<@NotNull @Valid EffectInput> effects) {}
    public record ConditionView(Long id, Long variableId, String operator, String expectedValue, Integer conditionGroup, Integer sortOrder) {}
    public record EffectView(Long id, Long variableId, String operation, String operandValue, Integer sortOrder) {}
    public record RulesView(Long choiceId, List<ConditionView> conditions, List<EffectView> effects) {}
}
