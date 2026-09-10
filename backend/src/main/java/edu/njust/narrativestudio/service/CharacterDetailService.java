package edu.njust.narrativestudio.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.njust.narrativestudio.dto.DatabaseDtos.*;
import edu.njust.narrativestudio.entity.*;
import edu.njust.narrativestudio.mapper.*;
import edu.njust.narrativestudio.exception.BusinessException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class CharacterDetailService {
    private final CharacterRelationMapper relations;
    private final CharacterKnowledgeMapper knowledge;
    private final NodeCharacterMapper cast;
    private final ProjectAccessService access;
    private final ProjectMutationGuard guard;
    private final FeatureScope scope;
    public CharacterDetailService(CharacterRelationMapper relations,CharacterKnowledgeMapper knowledge,
            NodeCharacterMapper cast,ProjectAccessService access,ProjectMutationGuard guard,FeatureScope scope) {
        this.relations=relations;this.knowledge=knowledge;this.cast=cast;this.access=access;this.guard=guard;this.scope=scope;
    }
    public List<CharacterRelation> relations(Long u,Long p) {
        access.requireMember(u,p);
        return relations.selectList(new LambdaQueryWrapper<CharacterRelation>().eq(CharacterRelation::getProjectId,p).orderByAsc(CharacterRelation::getId));
    }
    public CharacterRelation relation(Long u,Long p,Long id) { access.requireMember(u,p);return requireRelation(p,id); }
    @Transactional
    public CharacterRelation saveRelation(Long u,Long p,Long id,RelationRequest r) {
        guard.editor(u,p);
        scope.character(p,r.sourceCharacterId());scope.character(p,r.targetCharacterId());
        if(r.sourceCharacterId().equals(r.targetCharacterId())) throw FeatureScope.invalid("不能创建自身关系");
        CharacterRelation c=id==null?new CharacterRelation():requireRelation(p,id);
        c.setProjectId(p);c.setSourceCharacterId(r.sourceCharacterId());c.setTargetCharacterId(r.targetCharacterId());
        c.setRelationType(r.relationType().trim());c.setDescription(r.description());
        if(id==null) relations.insert(c);else relations.update(null,
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CharacterRelation>()
                .eq(CharacterRelation::getId,id).set(CharacterRelation::getSourceCharacterId,c.getSourceCharacterId())
                .set(CharacterRelation::getTargetCharacterId,c.getTargetCharacterId()).set(CharacterRelation::getRelationType,c.getRelationType())
                .set(CharacterRelation::getDescription,c.getDescription()));
        return c;
    }
    @Transactional
    public void deleteRelation(Long u,Long p,Long id) { guard.editor(u,p);requireRelation(p,id);relations.deleteById(id); }
    private CharacterRelation requireRelation(Long p,Long id) {
        CharacterRelation c=relations.selectById(id);
        if(c==null || !p.equals(c.getProjectId())) throw BusinessException.notFound("角色关系不存在");
        return c;
    }
    public List<CharacterKnowledge> knowledge(Long u,Long p,Long character) {
        access.requireMember(u,p);scope.character(p,character);
        return knowledge.selectList(new LambdaQueryWrapper<CharacterKnowledge>().eq(CharacterKnowledge::getProjectId,p)
                .eq(CharacterKnowledge::getCharacterId,character).orderByAsc(CharacterKnowledge::getId));
    }
    public CharacterKnowledge knowledgeItem(Long u,Long p,Long character,Long id) {
        access.requireMember(u,p);scope.character(p,character);return requireKnowledge(p,character,id);
    }
    @Transactional
    public CharacterKnowledge saveKnowledge(Long u,Long p,Long character,Long id,KnowledgeRequest r) {
        guard.editor(u,p);scope.character(p,character);if(r.acquiredNodeId()!=null) scope.node(p,r.acquiredNodeId());
        CharacterKnowledge k=id==null?new CharacterKnowledge():requireKnowledge(p,character,id);
        k.setProjectId(p);k.setCharacterId(character);k.setKnowledgeKey(r.knowledgeKey());k.setKnowledgeLevel(r.knowledgeLevel());
        k.setDescription(r.description());k.setAcquiredNodeId(r.acquiredNodeId());
        if(id==null) knowledge.insert(k);else {
            // Explicit SET allows clearing optional fields (MyBatis default skips null).
            knowledge.update(null,new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CharacterKnowledge>()
                .eq(CharacterKnowledge::getId,id).set(CharacterKnowledge::getKnowledgeKey,k.getKnowledgeKey())
                .set(CharacterKnowledge::getKnowledgeLevel,k.getKnowledgeLevel()).set(CharacterKnowledge::getDescription,k.getDescription())
                .set(CharacterKnowledge::getAcquiredNodeId,k.getAcquiredNodeId()));
        }
        return k;
    }
    @Transactional
    public void deleteKnowledge(Long u,Long p,Long character,Long id) {
        guard.editor(u,p);scope.character(p,character);requireKnowledge(p,character,id);knowledge.deleteById(id);
    }
    private CharacterKnowledge requireKnowledge(Long p,Long c,Long id) {
        CharacterKnowledge k=knowledge.selectById(id);
        if(k==null || !p.equals(k.getProjectId()) || !c.equals(k.getCharacterId())) throw BusinessException.notFound("角色知识不存在");
        return k;
    }
    public List<Long> cast(Long u,Long p,Long node) { access.requireMember(u,p);scope.node(p,node);return cast.characters(node); }
    @Transactional
    public List<Long> replaceCast(Long u,Long p,Long node,CastRequest r) {
        guard.editor(u,p);scope.node(p,node);
        if(new HashSet<>(r.characterIds()).size()!=r.characterIds().size()) throw FeatureScope.invalid("出场角色不能重复");
        r.characterIds().forEach(id->scope.character(p,id));
        cast.clear(node);r.characterIds().forEach(id->cast.add(node,id));return cast.characters(node);
    }
}
