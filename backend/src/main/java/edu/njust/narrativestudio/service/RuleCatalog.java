package edu.njust.narrativestudio.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.njust.narrativestudio.entity.*;
import edu.njust.narrativestudio.mapper.*;
import java.util.List;
import org.springframework.stereotype.Component;

/** Internal data access helper; public service entry points enforce project authorization. */
@Component
public class RuleCatalog {
    private final StateVariableMapper variables;
    private final ChoiceConditionMapper conditions;
    private final StateEffectMapper effects;
    public RuleCatalog(StateVariableMapper variables,ChoiceConditionMapper conditions,StateEffectMapper effects) {
        this.variables=variables; this.conditions=conditions; this.effects=effects;
    }
    public List<StateVariable> variables(Long project) {
        return variables.selectList(new LambdaQueryWrapper<StateVariable>().eq(StateVariable::getProjectId,project).orderByAsc(StateVariable::getId));
    }
    public List<ChoiceCondition> conditions(Long choice) {
        return conditions.selectList(new LambdaQueryWrapper<ChoiceCondition>().eq(ChoiceCondition::getChoiceId,choice)
                .orderByAsc(ChoiceCondition::getSortOrder).orderByAsc(ChoiceCondition::getId));
    }
    public List<StateEffect> effects(Long choice) {
        return effects.selectList(new LambdaQueryWrapper<StateEffect>().eq(StateEffect::getChoiceId,choice)
                .orderByAsc(StateEffect::getSortOrder).orderByAsc(StateEffect::getId));
    }
}
