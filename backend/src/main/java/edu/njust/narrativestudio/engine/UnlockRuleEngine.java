package edu.njust.narrativestudio.engine;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.njust.narrativestudio.dto.*;
import edu.njust.narrativestudio.entity.*;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class UnlockRuleEngine {
    private final ObjectMapper json;private final RuleEngine rules;
    public UnlockRuleEngine(ObjectMapper json,RuleEngine rules) {this.json=json;this.rules=rules;}
    public UnlockRule decode(String value) {
        if(value==null) return null;
        if(value.length()>32768) throw RuleEngine.invalid("解锁表达式过大");
        try {return json.readValue(value,UnlockRule.class);}
        catch(Exception ex) {throw RuleEngine.invalid("解锁表达式格式错误");}
    }
    public String encode(UnlockRule rule) {
        if(rule==null) return null;
        try {
            String text=json.writeValueAsString(rule);
            if(text.length()>32768) throw RuleEngine.invalid("解锁表达式过大");
            return text;
        } catch(com.fasterxml.jackson.core.JsonProcessingException ex) {throw RuleEngine.invalid("解锁表达式无法编码");}
    }
    public void validate(UnlockRule rule,List<StoryNode> nodes,List<StateVariable> variables) {
        if(rule==null) return;
        Map<String,StoryNode> ns=new HashMap<>();nodes.forEach(n->ns.put(n.getNodeKey(),n));
        Map<String,StateVariable> vs=new HashMap<>();variables.forEach(v->vs.put(v.getVariableKey(),v));
        check(rule,ns,vs,0,new int[]{0});encode(rule);
    }
    private void check(UnlockRule r,Map<String,StoryNode> ns,Map<String,StateVariable> vs,int depth,int[] total) {
        if(r==null || depth>=8 || ++total[0]>128 || r.type()==null) throw RuleEngine.invalid("解锁规则最多8层、128项，子条件不能为空");
        var children=r.children()==null?List.<UnlockRule>of():r.children();
        switch(r.type()) {
            case "ALL","ANY","NOT","AT_LEAST" -> {
                if(r.nodeKey()!=null || r.variableKey()!=null || r.operator()!=null || r.value()!=null || (!"AT_LEAST".equals(r.type()) && r.count()!=null))
                    throw RuleEngine.invalid("组合条件包含不适用字段");
                if(children.isEmpty() || children.size()>32 || ("NOT".equals(r.type()) && children.size()!=1))
                    throw RuleEngine.invalid("组合条件需1至32项；NOT恰好1项");
                if("AT_LEAST".equals(r.type()) && (r.count()==null || r.count()<1 || r.count()>children.size()))
                    throw RuleEngine.invalid("满足数量超出子条件数量");
                for(var c:children) check(c,ns,vs,depth+1,total);
                if("AT_LEAST".equals(r.type()) && children.stream().map(this::fingerprint).distinct().count()!=children.size())
                    throw RuleEngine.invalid("数量条件不能重复计算相同子条件");
            }
            case "ENDING","VISITED" -> {
                if(r.count()!=null || r.variableKey()!=null || r.operator()!=null || r.value()!=null)
                    throw RuleEngine.invalid("节点前置条件包含不适用字段");
                if(!children.isEmpty()) throw RuleEngine.invalid("叶条件不能包含子条件");
                StoryNode n=ns.get(r.nodeKey());
                if(n==null || ("ENDING".equals(r.type()) && !"ENDING".equals(n.getNodeType())))
                    throw RuleEngine.invalid("解锁条件引用了不存在的节点或非结局节点");
            }
            case "VARIABLE" -> {
                if(r.nodeKey()!=null || r.count()!=null) throw RuleEngine.invalid("变量条件包含不适用字段");
                if(!children.isEmpty()) throw RuleEngine.invalid("叶条件不能包含子条件");
                StateVariable v=vs.get(r.variableKey());if(v==null) throw RuleEngine.invalid("解锁条件引用了不存在的变量");
                ChoiceCondition c=new ChoiceCondition();c.setOperator(r.operator());c.setExpectedValue(r.value());c.setConditionGroup(0);
                rules.validateCondition(v,c);
            }
            default -> throw RuleEngine.invalid("未知解锁条件类型");
        }
    }
    public boolean available(UnlockRule r,List<StateVariable> vars,Map<String,String> state,ProgressSnapshot progress) {
        if(r==null) return true;
        return switch(r.type()) {
            case "ALL" -> r.children().stream().allMatch(c->available(c,vars,state,progress));
            case "ANY" -> r.children().stream().anyMatch(c->available(c,vars,state,progress));
            case "NOT" -> !available(r.children().getFirst(),vars,state,progress);
            case "AT_LEAST" -> r.children().stream().filter(c->available(c,vars,state,progress)).count()>=r.count();
            case "ENDING" -> progress.completedEndings().contains(r.nodeKey());
            case "VISITED" -> progress.visitedNodes().contains(r.nodeKey());
            case "VARIABLE" -> {
                var v=vars.stream().filter(x->x.getVariableKey().equals(r.variableKey())).findFirst().orElseThrow(()->RuleEngine.invalid("解锁变量不存在"));
                var c=new ChoiceCondition();c.setVariableId(v.getId());c.setOperator(r.operator());c.setExpectedValue(r.value());c.setConditionGroup(0);
                yield rules.available(vars,List.of(c),state);
            }
            default -> throw RuleEngine.invalid("未知解锁条件类型");
        };
    }
    private String fingerprint(UnlockRule rule) {
        // Ignore null-vs-empty children and order for commutative groups; do not count the same prerequisite twice.
        return switch(rule.type()) {
            case "ENDING","VISITED" -> rule.type()+":"+rule.nodeKey();
            case "VARIABLE" -> encode(new UnlockRule(rule.type(),null,null,null,rule.variableKey(),rule.operator(),rule.value()));
            default -> rule.type()+":"+rule.count()+":"+rule.children().stream().map(this::fingerprint).sorted().toList();
        };
    }
    public boolean references(UnlockRule rule,String key,boolean variable) {
        if(rule==null) return false;
        if(key.equals(variable?rule.variableKey():rule.nodeKey())) return true;
        return rule.children()!=null && rule.children().stream().anyMatch(r->references(r,key,variable));
    }
    public String describe(UnlockRule rule) {
        if(rule==null) return "无附加条件";
        return switch(rule.type()) {
            case "ENDING" -> "已通关结局 "+rule.nodeKey();
            case "VISITED" -> "已访问节点 "+rule.nodeKey();
            case "VARIABLE" -> rule.variableKey()+" "+rule.operator()+" "+rule.value();
            default -> (switch(rule.type()) {
                case "ALL" -> "全部满足";case "ANY" -> "任一满足";case "NOT" -> "不满足";
                case "AT_LEAST" -> "至少满足 "+rule.count()+" 项";default -> "未知条件";
            })+"（"+String.join("；",rule.children().stream().map(this::describe).toList())+"）";
        };
    }
}
