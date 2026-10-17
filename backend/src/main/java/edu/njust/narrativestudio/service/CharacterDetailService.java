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
    // 【解释】查询本项目角色关系，按 ID 排序；筛选或排序变化保留 projectId 条件。
    public List<CharacterRelation> relations(Long u,Long p) {
        access.requireMember(u,p);
        return relations.selectList(new LambdaQueryWrapper<CharacterRelation>().eq(CharacterRelation::getProjectId,p).orderByAsc(CharacterRelation::getId));
    }
    // 【解释】读取一条关系，先检查项目成员再检查关系归属。
    public CharacterRelation relation(Long u,Long p,Long id) { access.requireMember(u,p);return requireRelation(p,id); }
    @Transactional
    // 【解释】id 为 null 新增，否则修改；两端角色必须属于本项目且不能自己关联自己。
    // 【常见修改】关系类型最长 20 字符：DatabaseDtos.RelationRequest 的 @Size(max=40) 改为 20；同步前端。
    // 更新用显式 .set(...)，允许清空 description，不要随意换成跳过 null 的更新方式。
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
    // 【解释】编辑者验证归属后删除角色关系，不删除两端角色。
    public void deleteRelation(Long u,Long p,Long id) { guard.editor(u,p);requireRelation(p,id);relations.deleteById(id); }
    // 【解释】验证关系存在并属于本项目。
    private CharacterRelation requireRelation(Long p,Long id) {
        CharacterRelation c=relations.selectById(id);
        if(c==null || !p.equals(c.getProjectId())) throw BusinessException.notFound("角色关系不存在");
        return c;
    }
    // 【解释】按项目及角色查询知识定义；不是模拟过程中实时获得的知识状态。
    public List<CharacterKnowledge> knowledge(Long u,Long p,Long character) {
        access.requireMember(u,p);scope.character(p,character);
        return knowledge.selectList(new LambdaQueryWrapper<CharacterKnowledge>().eq(CharacterKnowledge::getProjectId,p)
                .eq(CharacterKnowledge::getCharacterId,character).orderByAsc(CharacterKnowledge::getId));
    }
    // 【解释】验证角色归属，再读取该角色的指定知识。
    public CharacterKnowledge knowledgeItem(Long u,Long p,Long character,Long id) {
        access.requireMember(u,p);scope.character(p,character);return requireKnowledge(p,character,id);
    }
    @Transactional
    // 【解释】保存知识标识、等级和获得节点；获得节点可为空，但不为空时必须属于项目。
    // 【修改提示】知识等级由 DatabaseDtos.KnowledgeRequest 白名单约束，新增等级需同步运行态、前端和快照兼容，不只加字符串。
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
    // 【解释】编辑权限与角色/知识归属验证后删除定义。
    public void deleteKnowledge(Long u,Long p,Long character,Long id) {
        guard.editor(u,p);scope.character(p,character);requireKnowledge(p,character,id);knowledge.deleteById(id);
    }
    // 【解释】项目、角色、知识 ID 三层匹配，避免跨角色修改。
    private CharacterKnowledge requireKnowledge(Long p,Long c,Long id) {
        CharacterKnowledge k=knowledge.selectById(id);
        if(k==null || !p.equals(k.getProjectId()) || !c.equals(k.getCharacterId())) throw BusinessException.notFound("角色知识不存在");
        return k;
    }
    // 【解释】查询一个节点关联的出场角色 ID 列表。
    public List<Long> cast(Long u,Long p,Long node) { access.requireMember(u,p);scope.node(p,node);return cast.characters(node); }
    @Transactional
    // 【解释】检查角色列表不重复且都属于本项目，再在事务中清空旧关联、插入完整新列表。
    // 【常见修改】最多 50 个角色：DatabaseDtos.CastRequest 的 @Size(max=200) 改为 50；空列表代表清空关联，保留该语义。
    public List<Long> replaceCast(Long u,Long p,Long node,CastRequest r) {
        guard.editor(u,p);scope.node(p,node);
        if(new HashSet<>(r.characterIds()).size()!=r.characterIds().size()) throw FeatureScope.invalid("出场角色不能重复");
        r.characterIds().forEach(id->scope.character(p,id));
        cast.clear(node);r.characterIds().forEach(id->cast.add(node,id));return cast.characters(node);
    }
}
