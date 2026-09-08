package edu.njust.narrativestudio.engine;

import edu.njust.narrativestudio.entity.*;
import edu.njust.narrativestudio.exception.BusinessException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Typed, deterministic rules; never evaluates scripts or mutates input snapshots. */
@Component
public class RuleEngine {
    public String validateValue(String type, String value) {
        if (value == null || value.length() > 500) throw invalid("规则值不能为空且不能超过500字符");
        switch (type == null ? "" : type) {
            case "BOOLEAN" -> { if (!Set.of("true", "false").contains(value)) throw invalid("BOOLEAN只接受true或false"); }
            case "INTEGER" -> {
                if (!value.matches("-?(0|[1-9][0-9]*)")) throw invalid("INTEGER必须为十进制整数");
                try { return Long.toString(Long.parseLong(value)); }
                catch (NumberFormatException e) { throw invalid("INTEGER超出64位有符号整数范围"); }
            }
            case "STRING" -> { }
            default -> throw invalid("不支持的变量类型");
        }
        return value;
    }
    public void validateCondition(StateVariable v, ChoiceCondition c) {
        Set<String> ops = "INTEGER".equals(v.getValueType()) ? Set.of("EQ","NE","GT","GTE","LT","LTE") : Set.of("EQ","NE");
        if (!ops.contains(c.getOperator() == null ? "" : c.getOperator())) throw invalid("条件操作符与变量类型不匹配");
        if (c.getConditionGroup()==null || c.getConditionGroup()<0) throw invalid("条件组必须为非负整数");
        validateValue(v.getValueType(),c.getExpectedValue());
    }
    public void validateEffect(StateVariable v, StateEffect e) {
        Set<String> ops = "INTEGER".equals(v.getValueType()) ? Set.of("SET","ADD","SUBTRACT") : Set.of("SET");
        if (!ops.contains(e.getOperation()==null ? "" : e.getOperation())) throw invalid("效果操作符与变量类型不匹配");
        validateValue(v.getValueType(),e.getOperandValue());
    }
    public Map<String,String> initialState(List<StateVariable> variables) {
        Map<String,String> state=new LinkedHashMap<>();
        for (StateVariable v:variables)
            if(state.put(v.getVariableKey(),validateValue(v.getValueType(),v.getInitialValue()))!=null) throw invalid("变量标识重复");
        return state;
    }
    public boolean available(List<StateVariable> variables,List<ChoiceCondition> conditions,Map<String,String> state) {
        if(conditions.isEmpty()) return true;
        Map<Long,StateVariable> byId=index(variables);
        Map<Integer,Boolean> groups=new HashMap<>();
        for(ChoiceCondition c:conditions) {
            StateVariable v=require(byId,c.getVariableId()); validateCondition(v,c);
            String l=validateValue(v.getValueType(),state.get(v.getVariableKey()));
            String r=validateValue(v.getValueType(),c.getExpectedValue());
            int n="INTEGER".equals(v.getValueType()) ? Long.compare(Long.parseLong(l),Long.parseLong(r)) : l.compareTo(r);
            boolean matches=switch(c.getOperator()) {
                case "EQ" -> n==0; case "NE" -> n!=0; case "GT" -> n>0;
                case "GTE" -> n>=0; case "LT" -> n<0; case "LTE" -> n<=0;
                default -> throw invalid("未知条件操作符");
            };
            groups.merge(c.getConditionGroup(),matches,(a,b)->a&&b);
        }
        return groups.values().stream().anyMatch(Boolean.TRUE::equals);
    }
    public Map<String,String> apply(List<StateVariable> variables,List<StateEffect> effects,Map<String,String> before) {
        Map<Long,StateVariable> byId=index(variables); Map<String,String> after=new LinkedHashMap<>(before);
        List<StateEffect> ordered=effects.stream().sorted(Comparator
                .comparing(StateEffect::getSortOrder,Comparator.nullsFirst(Integer::compareTo))
                .thenComparing(StateEffect::getId,Comparator.nullsFirst(Long::compareTo))).toList();
        for(StateEffect e:ordered) {
            StateVariable v=require(byId,e.getVariableId()); validateEffect(v,e);
            String current=validateValue(v.getValueType(),after.get(v.getVariableKey()));
            String operand=validateValue(v.getValueType(),e.getOperandValue());
            try {
                String next=switch(e.getOperation()) {
                    case "SET" -> operand;
                    case "ADD" -> Long.toString(Math.addExact(Long.parseLong(current),Long.parseLong(operand)));
                    case "SUBTRACT" -> Long.toString(Math.subtractExact(Long.parseLong(current),Long.parseLong(operand)));
                    default -> throw invalid("未知效果操作符");
                };
                after.put(v.getVariableKey(),next);
            } catch(ArithmeticException ex) { throw invalid("状态计算发生整数溢出"); }
        }
        return after;
    }
    private Map<Long,StateVariable> index(List<StateVariable> variables) {
        Map<Long,StateVariable> result=new HashMap<>(); variables.forEach(v->result.put(v.getId(),v)); return result;
    }
    private StateVariable require(Map<Long,StateVariable> variables,Long id) {
        StateVariable v=variables.get(id);
        if(v==null) throw invalid("规则引用了不存在或其他项目的变量");
        return v;
    }
    public static BusinessException invalid(String message) {
        return new BusinessException("RULE_VALUE_INVALID",message,HttpStatus.BAD_REQUEST);
    }
}
