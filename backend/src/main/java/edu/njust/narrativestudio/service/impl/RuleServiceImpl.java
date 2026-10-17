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
    private final edu.njust.narrativestudio.engine.UnlockRuleEngine unlock;
    private final StoryNodeMapper nodes;
    private final UnlockReferences references;
    public RuleServiceImpl(StateVariableMapper variables,ChoiceConditionMapper conditions,StateEffectMapper effects,
            StoryChoiceMapper choices,PlaytestSessionMapper sessions,ProjectAccessService access,
            ProjectMutationGuard guard,RuleCatalog catalog,RuleEngine engine,
            edu.njust.narrativestudio.engine.UnlockRuleEngine unlock,StoryNodeMapper nodes,UnlockReferences references) {
        this.variables=variables; this.conditions=conditions; this.effects=effects; this.choices=choices;
        this.sessions=sessions; this.access=access; this.guard=guard; this.catalog=catalog; this.engine=engine;
        this.unlock=unlock;this.nodes=nodes;this.references=references;
    }
    // 【解释】成员检查后读取项目变量定义，转换响应；不是当前某次模拟的变量值。
    public List<VariableView> listVariables(Long u,Long p) {
        access.requireMember(u,p); return catalog.variables(p).stream().map(this::view).toList();
    }
    // 【解释】读单个本项目变量。
    public VariableView getVariable(Long u,Long p,Long id) { access.requireMember(u,p); return view(variable(p,id)); }
    @Transactional
    // 【解释】编辑者加锁，拒绝在有运行会话时改变量；检查数量和标识唯一，再校验类型与初始值后插入。
    // 【常见修改】变量数量 200 的限制需同步通用导入 DTO、跨局测试存档容量和前端，不只改提示。
    public VariableView createVariable(Long u,Long p,VariableRequest r) {
        guard.editor(u,p); noActiveSession(p);
        if(variables.selectCount(new LambdaQueryWrapper<StateVariable>().eq(StateVariable::getProjectId,p))>=200)
            throw BusinessException.conflict("每项目最多200个状态变量");
        unique(p,r.variableKey(),null);
        StateVariable v=new StateVariable(); v.setProjectId(p); apply(v,r); variables.insert(v); return view(v);
    }
    @Transactional
    // 【解释】变量标识、类型、持久化范围变化时保护解锁/进度引用；类型变化还要检查条件效果引用。
    public VariableView updateVariable(Long u,Long p,Long id,VariableRequest r) {
        guard.editor(u,p); StateVariable v=variable(p,id); noActiveSession(p); unique(p,r.variableKey(),id);
        if(!v.getVariableKey().equals(r.variableKey()) || !v.getValueType().equals(r.valueType()) || !v.getPersistenceScope().equals(r.persistenceScope()))
            references.requireUnused(p,v.getVariableKey(),true);
        if(!v.getValueType().equals(r.valueType()) && referenced(id)) throw inUse("变量已被规则引用，不能修改类型");
        apply(v,r); variables.updateById(v); return view(v);
    }
    @Transactional
    // 【解释】无运行会话且没有条件、效果或高级解锁/进度引用才可删除。
    public void deleteVariable(Long u,Long p,Long id) {
        guard.editor(u,p); variable(p,id); noActiveSession(p);
        references.requireUnused(p,variable(p,id).getVariableKey(),true);
        if(referenced(id)) throw inUse("变量被条件或效果引用，请先修改关联规则");
        variables.deleteById(id);
    }
    // 【解释】验证选项所属项目/起点，返回条件、效果及解锁树。
    public RulesView getRules(Long u,Long p,Long n,Long c) {
        access.requireMember(u,p); choice(p,n,c); return ruleView(c,catalog.conditions(c),catalog.effects(c));
    }
    @Transactional
    // 【解释】先完整校验解锁树和所有条件/效果，再在事务中删除旧规则、插入新规则。
    // 【修改提示】单项数上限在 RuleDtos.RulesRequest；扩大时核对通用导入 DTO。不能边校验边提交删除。
    public RulesView replaceRules(Long u,Long p,Long n,Long c,RulesRequest r) {
        guard.editor(u,p); choice(p,n,c);
        unlock.validate(r.unlockRule(),nodes.selectList(new LambdaQueryWrapper<StoryNode>().eq(StoryNode::getProjectId,p)),catalog.variables(p));
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
        choices.update(null,new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<StoryChoice>()
                .eq(StoryChoice::getId,c).set(StoryChoice::getUnlockRule,unlock.encode(r.unlockRule())));
        return ruleView(c,cs,es);
    }
    // 【解释】检查项目是否有 RUNNING 模拟，保护运行状态与变量定义的一致性。
    private void noActiveSession(Long p) {
        if(sessions.selectCount(new LambdaQueryWrapper<PlaytestSession>().eq(PlaytestSession::getProjectId,p)
                .eq(PlaytestSession::getStatus,"RUNNING"))>0) throw BusinessException.conflict("请先结束项目中运行中的模拟，再编辑变量");
    }
    // 【解释】检查变量是否被普通条件/效果使用；高级引用由 UnlockReferences 另查。
    private boolean referenced(Long id) {
        return conditions.selectCount(new LambdaQueryWrapper<ChoiceCondition>().eq(ChoiceCondition::getVariableId,id))>0
                || effects.selectCount(new LambdaQueryWrapper<StateEffect>().eq(StateEffect::getVariableId,id))>0;
    }
    // 【解释】项目内变量标识查重，更新排除自己。
    private void unique(Long p,String key,Long except) {
        var q=new LambdaQueryWrapper<StateVariable>().eq(StateVariable::getProjectId,p).eq(StateVariable::getVariableKey,key);
        if(except!=null) q.ne(StateVariable::getId,except);
        if(variables.selectCount(q)>0) throw BusinessException.conflict("项目内变量标识已存在");
    }
    // 【解释】验证变量所属项目。
    private StateVariable variable(Long p,Long id) {
        StateVariable v=variables.selectById(id);
        if(v==null || !p.equals(v.getProjectId())) throw BusinessException.notFound("变量不存在"); return v;
    }
    // 【解释】验证项目和起点对应的选项。
    private void choice(Long p,Long n,Long id) {
        StoryChoice c=choices.selectById(id);
        if(c==null || !p.equals(c.getProjectId()) || !n.equals(c.getSourceNodeId())) throw BusinessException.notFound("选择不存在");
    }
    // 【解释】写变量定义，初始值经 RuleEngine 校验规范化；持久化范围来自请求。
    private void apply(StateVariable v,VariableRequest r) {
        v.setVariableKey(r.variableKey()); v.setDisplayName(r.displayName().trim()); v.setValueType(r.valueType());
        v.setInitialValue(engine.validateValue(r.valueType(),r.initialValue())); v.setDescription(r.description());
        v.setPersistenceScope(r.persistenceScope());
    }
    // 【解释】变量实体转响应，不包含模拟中动态数值。
    private VariableView view(StateVariable v) {
        return new VariableView(v.getId(),v.getProjectId(),v.getVariableKey(),v.getDisplayName(),v.getValueType(),v.getInitialValue(),v.getDescription(),v.getPersistenceScope());
    }
    // 【解释】组合条件/效果列表并解析选项 JSON 解锁树。
    private RulesView ruleView(Long id,List<ChoiceCondition> cs,List<StateEffect> es) {
        return new RulesView(id,cs.stream().map(c->new ConditionView(c.getId(),c.getVariableId(),c.getOperator(),c.getExpectedValue(),c.getConditionGroup(),c.getSortOrder())).toList(),
                es.stream().map(e->new EffectView(e.getId(),e.getVariableId(),e.getOperation(),e.getOperandValue(),e.getSortOrder())).toList(),
                unlock.decode(choices.selectById(id).getUnlockRule()));
    }
    // 【解释】被引用时返回 RESOURCE_IN_USE/409，不能只为删除成功而吞掉异常。
    private BusinessException inUse(String message) { return new BusinessException("RESOURCE_IN_USE",message,HttpStatus.CONFLICT); }
}
