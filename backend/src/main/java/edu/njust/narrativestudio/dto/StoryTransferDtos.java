package edu.njust.narrativestudio.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import edu.njust.narrativestudio.dto.StoryGraphDtos.NodeRequest;
import edu.njust.narrativestudio.dto.RuleDtos.VariableRequest;

public final class StoryTransferDtos {
    private StoryTransferDtos() {}
    public record Document(
            @NotNull @Min(1) @Max(2) Integer schemaVersion,
            @NotBlank @Size(max=100) String name,
            @Size(max=1000) String description,
            @NotNull @Size(max=500) List<@NotNull @Valid NodeRequest> nodes,
            @NotNull @Size(max=200) List<@NotNull @Valid VariableRequest> variables,
            @NotNull @Size(max=1000) List<@NotNull @Valid Choice> choices) {}
    public record Choice(
            @NotBlank String sourceNodeKey, @NotBlank String targetNodeKey,
            @NotBlank @Size(max=500) String choiceText, Integer sortOrder, @NotNull Boolean enabled,
            @NotNull @Size(max=100) List<@NotNull @Valid Condition> conditions,
            @NotNull @Size(max=100) List<@NotNull @Valid Effect> effects,UnlockRule unlockRule) {
        public Choice(String source,String target,String text,Integer order,Boolean enabled,List<Condition> conditions,List<Effect> effects) {
            this(source,target,text,order,enabled,conditions,effects,null);
        }
    }
    public record Condition(@NotBlank String variableKey,
            @NotNull @Pattern(regexp="EQ|NE|GT|GTE|LT|LTE") String operator,
            @NotNull @Size(max=500) String expectedValue,
            @NotNull @Min(0) @Max(100) Integer conditionGroup) {}
    public record Effect(@NotBlank String variableKey,
            @NotNull @Pattern(regexp="SET|ADD|SUBTRACT") String operation,
            @NotNull @Size(max=500) String operandValue) {}
}
