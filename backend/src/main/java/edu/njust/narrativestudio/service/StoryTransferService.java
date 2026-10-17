package edu.njust.narrativestudio.service;

import edu.njust.narrativestudio.dto.*;
import edu.njust.narrativestudio.dto.StoryTransferDtos.*;
import jakarta.validation.Validator;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoryTransferService {
    private final ProjectService projects;
    private final StoryGraphService graph;
    private final RuleService rules;
    private final Validator validator;
    public StoryTransferService(ProjectService projects, StoryGraphService graph, RuleService rules, Validator validator) {
        this.projects=projects; this.graph=graph; this.rules=rules; this.validator=validator;
    }

    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    // 【解释】读取项目、图和规则，把数据库 ID 转为 nodeKey/variableKey，生成可迁移文档，不导出账号凭据或模拟历史。
    public Document exportStory(Long user, Long project) {
        var p=projects.getAccessible(user,project);
        var g=graph.getGraph(user,project);
        var variables=rules.listVariables(user,project);
        Map<Long,String> nodeKeys=new HashMap<>(), variableKeys=new HashMap<>();
        g.nodes().forEach(n->nodeKeys.put(n.id(),n.nodeKey()));
        variables.forEach(v->variableKeys.put(v.id(),v.variableKey()));
        var nodes=g.nodes().stream().map(n->new StoryGraphDtos.NodeRequest(n.nodeKey(),n.title(),n.content(),
                n.nodeType(),n.scene(),n.isStart(),n.positionX(),n.positionY())).toList();
        var vs=variables.stream().map(v->new RuleDtos.VariableRequest(v.variableKey(),v.displayName(),
                v.valueType(),v.initialValue(),v.description(),v.persistenceScope())).toList();
        var choices=g.choices().stream().map(c->{
            var r=rules.getRules(user,project,c.sourceNodeId(),c.id());
            return new Choice(required(nodeKeys,c.sourceNodeId()),required(nodeKeys,c.targetNodeId()),c.choiceText(),
                    c.sortOrder(),c.enabled(),
                    r.conditions().stream().map(x->new Condition(required(variableKeys,x.variableId()),
                            x.operator(),x.expectedValue(),x.conditionGroup())).toList(),
                    r.effects().stream().map(x->new Effect(required(variableKeys,x.variableId()),
                            x.operation(),x.operandValue())).toList(),r.unlockRule());
        }).toList();
        var result=new Document(2,p.name(),p.description(),nodes,vs,choices);
        validate(result);
        return result;
    }

    // Import is atomic and always creates a new owned project; exported IDs are never trusted.
    @Transactional
    public ProjectDtos.Summary importStory(Long user, Document document) {
        // 【Excel 链路的最终落库入口】通用方法也支持其他剧情导入，修改这里会影响不止 Excel。
        // 先校验文档和标识唯一性，再创建项目、节点和变量，最后创建引用它们的选项与规则。
        // nodeIds/variableIds 将稳定业务编号映射为新数据库 ID，不接受外部文件指定数据库主键。
        // Excel 当前生成的变量/条件/效果为空；不要因为此方法能导入规则就声称 Excel 能理解自然语言规则。
        // 【联动修改】扩大 Excel 节点/连线上限后仍需通过 Document 的 @Size 等约束，不能直接跳过 validate。
        validate(document);
        Set<String> nodeKeys=new HashSet<>(), variableKeys=new HashSet<>();
        for(var n:document.nodes()) unique(nodeKeys,n.nodeKey());
        for(var v:document.variables()) unique(variableKeys,v.variableKey());
        var p=projects.create(user,new ProjectDtos.CreateRequest(document.name(),document.description()));
        Map<String,Long> nodeIds=new HashMap<>(), variableIds=new HashMap<>();
        for(var n:document.nodes()) nodeIds.put(n.nodeKey(),graph.createNode(user,p.id(),n).id());
        for(var v:document.variables()) variableIds.put(v.variableKey(),rules.createVariable(user,p.id(),v).id());
        for(var c:document.choices()) {
            Long source=required(nodeIds,c.sourceNodeKey()),target=required(nodeIds,c.targetNodeKey());
            var saved=graph.createChoice(user,p.id(),source,
                    new StoryGraphDtos.ChoiceRequest(target,c.choiceText(),c.sortOrder(),c.enabled()));
            rules.replaceRules(user,p.id(),source,saved.id(),new RuleDtos.RulesRequest(
                    c.conditions().stream().map(x->new RuleDtos.ConditionInput(required(variableIds,x.variableKey()),
                            x.operator(),x.expectedValue(),x.conditionGroup())).toList(),
                    c.effects().stream().map(x->new RuleDtos.EffectInput(required(variableIds,x.variableKey()),
                            x.operation(),x.operandValue())).toList(),c.unlockRule()));
        }
        return p;
    }
    // 【解释】检查 DTO 约束和 schemaVersion；旧版本文档不能携带跨局变量/高级规则。
    private void validate(Document document) {
        if(document==null || !validator.validate(document).isEmpty()) throw FeatureScope.invalid("剧情文件格式或容量不符合 schemaVersion=1/2");
        if(document.schemaVersion()==1 && (document.variables().stream().anyMatch(v->!"SESSION".equals(v.persistenceScope()))
                || document.choices().stream().anyMatch(c->c.unlockRule()!=null)))
            throw FeatureScope.invalid("跨局变量和高级解锁规则需要 schemaVersion=2");
    }
    // 【解释】不区分大小写检查重复业务标识。
    private static void unique(Set<String> keys,String key) {
        if(!keys.add(key.toLowerCase(Locale.ROOT))) throw FeatureScope.invalid("剧情文件包含重复标识: "+key);
    }
    // 【解释】查编号映射，缺失引用就拒绝导入/导出，不生成悬空连线。
    private static <K,V> V required(Map<K,V> map,K key) {
        V value=map.get(key);
        if(value==null) throw FeatureScope.invalid("剧情文件包含无效引用");
        return value;
    }
}
