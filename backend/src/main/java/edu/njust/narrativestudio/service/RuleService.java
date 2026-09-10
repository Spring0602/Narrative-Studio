package edu.njust.narrativestudio.service;
import edu.njust.narrativestudio.dto.RuleDtos.*;
import java.util.List;

public interface RuleService {
    List<VariableView> listVariables(Long user,Long project);
    VariableView getVariable(Long user,Long project,Long id);
    VariableView createVariable(Long user,Long project,VariableRequest request);
    VariableView updateVariable(Long user,Long project,Long id,VariableRequest request);
    void deleteVariable(Long user,Long project,Long id);
    RulesView getRules(Long user,Long project,Long node,Long choice);
    RulesView replaceRules(Long user,Long project,Long node,Long choice,RulesRequest request);
}
