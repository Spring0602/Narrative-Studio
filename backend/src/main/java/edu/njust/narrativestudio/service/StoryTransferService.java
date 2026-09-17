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
    private void validate(Document document) {
        if(document==null || !validator.validate(document).isEmpty()) throw FeatureScope.invalid("剧情文件格式或容量不符合 schemaVersion=1/2");
        if(document.schemaVersion()==1 && (document.variables().stream().anyMatch(v->!"SESSION".equals(v.persistenceScope()))
                || document.choices().stream().anyMatch(c->c.unlockRule()!=null)))
            throw FeatureScope.invalid("跨局变量和高级解锁规则需要 schemaVersion=2");
    }
    private static void unique(Set<String> keys,String key) {
        if(!keys.add(key.toLowerCase(Locale.ROOT))) throw FeatureScope.invalid("剧情文件包含重复标识: "+key);
    }
    private static <K,V> V required(Map<K,V> map,K key) {
        V value=map.get(key);
        if(value==null) throw FeatureScope.invalid("剧情文件包含无效引用");
        return value;
    }
}
