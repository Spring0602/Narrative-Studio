package edu.njust.narrativestudio.service;
import edu.njust.narrativestudio.dto.RuleDtos.*;
import java.util.List;

// 【业务契约】声明 RuleService 的可调用方法，具体逻辑在 impl/RuleServiceImpl.java；返回类型和参数是调用约定。
// 【修改提示】添加查询参数须同步 Controller、接口、实现类与测试调用；只改排序/提示/DTO 内部字段通常不用改接口签名。
public interface RuleService {
    List<VariableView> listVariables(Long user,Long project);
    VariableView getVariable(Long user,Long project,Long id);
    VariableView createVariable(Long user,Long project,VariableRequest request);
    VariableView updateVariable(Long user,Long project,Long id,VariableRequest request);
    void deleteVariable(Long user,Long project,Long id);
    RulesView getRules(Long user,Long project,Long node,Long choice);
    RulesView replaceRules(Long user,Long project,Long node,Long choice,RulesRequest request);
}
