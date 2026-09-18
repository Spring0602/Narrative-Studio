package edu.njust.narrativestudio.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.njust.narrativestudio.dto.ReleaseSnapshot;
import edu.njust.narrativestudio.entity.*;
import edu.njust.narrativestudio.mapper.*;
import edu.njust.narrativestudio.engine.RuleEngine;
import edu.njust.narrativestudio.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
@Transactional(readOnly=true)
public class ReleaseService {
    public record Summary(Long id,Long projectId,Integer versionNo,Long publishedBy,LocalDateTime publishedAt,Integer schemaVersion) {}
    private final StoryReleaseMapper releases;private final StoryNodeMapper nodes;private final StoryChoiceMapper choices;
    private final WorldEntryMapper world;private final CharacterProfileMapper characters;private final CharacterRelationMapper relations;
    private final CharacterKnowledgeMapper knowledge;private final NodeCharacterMapper cast;private final RuleCatalog catalog;
    private final RuleEngine engine;private final ProjectAccessService access;private final ProjectMutationGuard guard;private final ObjectMapper json;
    private final edu.njust.narrativestudio.engine.UnlockRuleEngine unlock;
    public ReleaseService(StoryReleaseMapper releases,StoryNodeMapper nodes,StoryChoiceMapper choices,WorldEntryMapper world,
            CharacterProfileMapper characters,CharacterRelationMapper relations,CharacterKnowledgeMapper knowledge,NodeCharacterMapper cast,
            RuleCatalog catalog,RuleEngine engine,ProjectAccessService access,ProjectMutationGuard guard,ObjectMapper json,
            edu.njust.narrativestudio.engine.UnlockRuleEngine unlock) {
        this.releases=releases;this.nodes=nodes;this.choices=choices;this.world=world;this.characters=characters;this.relations=relations;
        this.knowledge=knowledge;this.cast=cast;this.catalog=catalog;this.engine=engine;this.access=access;this.guard=guard;this.json=json;
        this.unlock=unlock;
    }
    public PlaytestService.Page<Summary> list(Long u,Long p,int page,int size) {
        access.requireMember(u,p);String limit=FeatureScope.limit(page,size);
        var q=new LambdaQueryWrapper<StoryRelease>().eq(StoryRelease::getProjectId,p);
        long total=releases.selectCount(q);
        return new PlaytestService.Page<>(releases.selectList(q.orderByDesc(StoryRelease::getVersionNo).last(limit)).stream().map(this::summary).toList(),page,size,total,(total+size-1)/size);
    }
    public ReleaseSnapshot get(Long u,Long p,Long id) { access.requireMember(u,p);return snapshot(p,id); }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Summary publish(Long u,Long p) {
        guard.editor(u,p);access.requireOwner(u,p);
        var ns=nodes.selectList(new LambdaQueryWrapper<StoryNode>().eq(StoryNode::getProjectId,p).orderByAsc(StoryNode::getId));
        if(ns.isEmpty() || ns.size()>500 || ns.stream().filter(n->Boolean.TRUE.equals(n.getIsStart())).count()!=1)
            throw FeatureScope.invalid("发布须有唯一起点且节点数为1至500");
        var cs=choices.selectList(new LambdaQueryWrapper<StoryChoice>().eq(StoryChoice::getProjectId,p).orderByAsc(StoryChoice::getSortOrder).orderByAsc(StoryChoice::getId));
        Map<Long,StoryNode> nodeMap=new HashMap<>();ns.forEach(n->nodeMap.put(n.getId(),n));
        List<StateVariable> vs=catalog.variables(p);engine.initialState(vs);
        Map<Long,StateVariable> variableMap=new HashMap<>();vs.forEach(v->variableMap.put(v.getId(),v));
        List<ChoiceCondition> conditions=new ArrayList<>();List<StateEffect> effects=new ArrayList<>();
        for(StoryChoice c:cs) {
            unlock.validate(unlock.decode(c.getUnlockRule()),ns,vs);
            if(!nodeMap.containsKey(c.getSourceNodeId()) || !nodeMap.containsKey(c.getTargetNodeId())
                    || "ENDING".equals(nodeMap.get(c.getSourceNodeId()).getNodeType())) throw FeatureScope.invalid("发布图存在非法连接");
            for(ChoiceCondition condition:catalog.conditions(c.getId())) {
                StateVariable v=variableMap.get(condition.getVariableId());if(v==null) throw FeatureScope.invalid("条件引用了项目外变量");
                engine.validateCondition(v,condition);conditions.add(condition);
            }
            for(StateEffect effect:catalog.effects(c.getId())) {
                StateVariable v=variableMap.get(effect.getVariableId());if(v==null) throw FeatureScope.invalid("效果引用了项目外变量");
                engine.validateEffect(v,effect);effects.add(effect);
            }
        }
        var chars=characters.selectList(new LambdaQueryWrapper<CharacterProfile>().eq(CharacterProfile::getProjectId,p).eq(CharacterProfile::getStatus,"ACTIVE").orderByAsc(CharacterProfile::getId));
        Set<Long> active=new HashSet<>();chars.forEach(c->active.add(c.getId()));
        var ks=knowledge.selectList(new LambdaQueryWrapper<CharacterKnowledge>().eq(CharacterKnowledge::getProjectId,p).orderByAsc(CharacterKnowledge::getId))
            .stream().filter(k->active.contains(k.getCharacterId())).toList();
        for(var k:ks) if(k.getAcquiredNodeId()!=null && !nodeMap.containsKey(k.getAcquiredNodeId())) throw FeatureScope.invalid("知识引用了项目外节点");
        var rs=relations.selectList(new LambdaQueryWrapper<CharacterRelation>().eq(CharacterRelation::getProjectId,p).orderByAsc(CharacterRelation::getId))
            .stream().filter(r->active.contains(r.getSourceCharacterId()) && active.contains(r.getTargetCharacterId())).toList();
        Map<Long,List<Long>> casts=new LinkedHashMap<>();ns.forEach(n->casts.put(n.getId(),cast.characters(n.getId()).stream().filter(active::contains).toList()));
        ReleaseSnapshot snapshot=new ReleaseSnapshot(access.requireProject(p),ns,cs,vs,conditions,effects,
            world.selectList(new LambdaQueryWrapper<WorldEntry>().eq(WorldEntry::getProjectId,p).orderByAsc(WorldEntry::getId)),chars,rs,ks,casts);
        var last=releases.selectOne(new LambdaQueryWrapper<StoryRelease>().eq(StoryRelease::getProjectId,p).orderByDesc(StoryRelease::getVersionNo).last("LIMIT 1"));
        StoryRelease r=new StoryRelease();r.setProjectId(p);r.setVersionNo(last==null?1:Math.incrementExact(last.getVersionNo()));
        r.setPublishedBy(u);r.setPublishedAt(LocalDateTime.now());r.setSchemaVersion(2);
        try { r.setContentSnapshot(json.writeValueAsString(snapshot)); }
        catch(Exception ex) { throw BusinessException.conflict("发布快照序列化失败"); }
        releases.insert(r);return summary(r);
    }
    public ReleaseSnapshot snapshot(Long p,Long id) {
        StoryRelease r=releases.selectById(id);
        if(r==null || !p.equals(r.getProjectId())) throw BusinessException.notFound("发布版本不存在");
        if(!Set.of(1,2).contains(r.getSchemaVersion())) throw BusinessException.conflict("不支持该发布快照版本");
        try { return json.readValue(r.getContentSnapshot(),ReleaseSnapshot.class); }
        catch(Exception ex) { throw BusinessException.conflict("发布快照损坏"); }
    }
    /** Published IDs must survive because the existing step schema uses foreign keys to authoring rows. */
    public void requireUnpublished(Long p,Long id,boolean node) {
        for(var r:releases.selectList(new LambdaQueryWrapper<StoryRelease>().eq(StoryRelease::getProjectId,p))) {
            ReleaseSnapshot s=snapshot(p,r.getId());
            boolean used=node?s.nodes().stream().anyMatch(n->id.equals(n.getId())):s.choices().stream().anyMatch(c->id.equals(c.getId()));
            if(used) throw BusinessException.conflict("对象已被发布版本引用，不能物理删除；可编辑或停用");
        }
    }
    private Summary summary(StoryRelease r) { return new Summary(r.getId(),r.getProjectId(),r.getVersionNo(),r.getPublishedBy(),r.getPublishedAt(),r.getSchemaVersion()); }
}
