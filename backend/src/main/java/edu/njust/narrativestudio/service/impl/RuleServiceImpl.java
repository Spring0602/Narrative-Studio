package edu.njust.narrativestudio.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.njust.narrativestudio.dto.RuleDtos.*;
import edu.njust.narrativestudio.engine.RuleEngine;
import edu.njust.narrativestudio.entity.*;
import edu.njust.narrativestudio.exception.BusinessException;
import edu.njust.narrativestudio.mapper.*;
import edu.njust.narrativestudio.service.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class RuleServiceImpl implements RuleService {
    private final StateVariableMapper variables;
    private final ChoiceConditionMapper conditions;
    private final StateEffectMapper effects;
    private final StoryChoiceMapper choices;
    private final PlaytestSessionMapper sessions;
    private final ProjectAccessService access;
    private final ProjectMutationGuard guard;
    private final RuleCatalog catalog;
    private final RuleEngine engine;
    public RuleServiceImpl(StateVariableMapper variables,ChoiceConditionMapper conditions,StateEffectMapper effects,
            StoryChoiceMapper choices,PlaytestSessionMapper sessions,ProjectAccessService access,
            ProjectMutationGuard guard,RuleCatalog catalog,RuleEngine engine) {
        this.variables=variables; this.conditions=conditions; this.effects=effects; this.choices=choices;
        this.sessions=sessions; this.access=access; this.guard=guard; this.catalog=catalog; this.engine=engine;
    }
    public List<VariableView> listVariables(Long u,Long p) {
        access.requireMember(u,p); return catalog.variables(p).stream().map(this::view).toList();
    }
    public VariableView getVariable(Long u,Long p,Long id) { access.requireMember(u,p); return view(variable(p,id)); }
    @Transactional
    public VariableView createVariable(Long u,Long p,VariableRequest r) {
        guard.editor(u,p); noActiveSession(p);
        if(variables.selectCount(new LambdaQueryWrapper<StateVariable>().eq(StateVariable::getProjectId,p))>=200)
            throw BusinessException.conflict("每项目最多200个状态变量");
        unique(p,r.variableKey(),null);
        StateVariable v=new StateVariable(); v.setProjectId(p); apply(v,r); variables.insert(v); return view(v);
    }
    @Transactional
    public VariableView updateVariable(Long u,Long p,Long id,VariableRequest r) {
        guard.editor(u,p); StateVariable v=variable(p,id); noActiveSession(p); unique(p,r.variableKey(),id);
        if(!v.getValueType().equals(r.valueType()) && referenced(id)) throw inUse("变量已被规则引用，不能修改类型");
        apply(v,r); variables.updateById(v); return view(v);
    }
    @Transactional
    public void deleteVariable(Long u,Long p,Long id) {
        guard.editor(u,p); variable(p,id); noActiveSession(p);
        if(referenced(id)) throw inUse("变量被条件或效果引用，请先修改关联规则");
        variables.deleteById(id);
    }
    public RulesView getRules(Long u,Long p,Long n,Long c) {
        access.requireMember(u,p); choice(p,n,c); return ruleView(c,catalog.conditions(c),catalog.effects(c));
    }
    @Transactional
    public RulesView replaceRules(Long u,Long p,Long n,Long c,RulesRequest r) {
        guard.editor(u,p); choice(p,n,c);
        Map<Long,StateVariable> byId=new HashMap<>(); catalog.variables(p).forEach(v->byId.put(v.getId(),v));
        List<ChoiceCondition> cs=new ArrayList<>(); List<StateEffect> es=new ArrayList<>();
        for(ConditionInput input:r.conditions()) {
            StateVariable v=byId.get(input.variableId());
            if(v==null) throw RuleEngine.invalid("条件引用了其他项目或不存在的变量");
            ChoiceCondition row=new ChoiceCondition(); row.setChoiceId(c); row.setVariableId(input.variableId());
            row.setOperator(input.operator()); row.setExpectedValue(input.expectedValue());
            row.setConditionGroup(input.conditionGroup()); row.setSortOrder(cs.size());
            engine.validateCondition(v,row); row.setExpectedValue(engine.validateValue(v.getValueType(),input.expectedValue())); cs.add(row);
        }
        for(EffectInput input:r.effects()) {
            StateVariable v=byId.get(input.variableId());
            if(v==null) throw RuleEngine.invalid("效果引用了其他项目或不存在的变量");
            StateEffect row=new StateEffect(); row.setChoiceId(c); row.setVariableId(input.variableId());
            row.setOperation(input.operation()); row.setOperandValue(input.operandValue()); row.setSortOrder(es.size());
            engine.validateEffect(v,row); row.setOperandValue(engine.validateValue(v.getValueType(),input.operandValue())); es.add(row);
        }
        // Validate the entire replacement before deleting the old aggregate.
        conditions.delete(new LambdaQueryWrapper<ChoiceCondition>().eq(ChoiceCondition::getChoiceId,c));
        effects.delete(new LambdaQueryWrapper<StateEffect>().eq(StateEffect::getChoiceId,c));
        cs.forEach(conditions::insert); es.forEach(effects::insert);
        return ruleView(c,cs,es);
    }
    private void noActiveSession(Long p) {
        if(sessions.selectCount(new LambdaQueryWrapper<PlaytestSession>().eq(PlaytestSession::getProjectId,p)
                .eq(PlaytestSession::getStatus,"RUNNING"))>0) throw BusinessException.conflict("请先结束项目中运行中的模拟，再编辑变量");
    }
    private boolean referenced(Long id) {
        return conditions.selectCount(new LambdaQueryWrapper<ChoiceCondition>().eq(ChoiceCondition::getVariableId,id))>0
                || effects.selectCount(new LambdaQueryWrapper<StateEffect>().eq(StateEffect::getVariableId,id))>0;
    }
    private void unique(Long p,String key,Long except) {
        var q=new LambdaQueryWrapper<StateVariable>().eq(StateVariable::getProjectId,p).eq(StateVariable::getVariableKey,key);
        if(except!=null) q.ne(StateVariable::getId,except);
        if(variables.selectCount(q)>0) throw BusinessException.conflict("项目内变量标识已存在");
    }
    private StateVariable variable(Long p,Long id) {
        StateVariable v=variables.selectById(id);
        if(v==null || !p.equals(v.getProjectId())) throw BusinessException.notFound("变量不存在"); return v;
    }
    private void choice(Long p,Long n,Long id) {
        StoryChoice c=choices.selectById(id);
        if(c==null || !p.equals(c.getProjectId()) || !n.equals(c.getSourceNodeId())) throw BusinessException.notFound("选择不存在");
    }
    private void apply(StateVariable v,VariableRequest r) {
        v.setVariableKey(r.variableKey()); v.setDisplayName(r.displayName().trim()); v.setValueType(r.valueType());
        v.setInitialValue(engine.validateValue(r.valueType(),r.initialValue())); v.setDescription(r.description());
    }
    private VariableView view(StateVariable v) {
        return new VariableView(v.getId(),v.getProjectId(),v.getVariableKey(),v.getDisplayName(),v.getValueType(),v.getInitialValue(),v.getDescription());
    }
    private RulesView ruleView(Long id,List<ChoiceCondition> cs,List<StateEffect> es) {
        return new RulesView(id,cs.stream().map(c->new ConditionView(c.getId(),c.getVariableId(),c.getOperator(),c.getExpectedValue(),c.getConditionGroup(),c.getSortOrder())).toList(),
                es.stream().map(e->new EffectView(e.getId(),e.getVariableId(),e.getOperation(),e.getOperandValue(),e.getSortOrder())).toList());
    }
    private BusinessException inUse(String message) { return new BusinessException("RESOURCE_IN_USE",message,HttpStatus.CONFLICT); }
}
