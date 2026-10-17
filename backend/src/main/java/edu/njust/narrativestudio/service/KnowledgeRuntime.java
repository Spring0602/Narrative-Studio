package edu.njust.narrativestudio.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.njust.narrativestudio.entity.*;
import edu.njust.narrativestudio.mapper.*;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class KnowledgeRuntime {
    private final CharacterKnowledgeMapper knowledge;private final CharacterProfileMapper characters;
    public KnowledgeRuntime(CharacterKnowledgeMapper knowledge,CharacterProfileMapper characters) {this.knowledge=knowledge;this.characters=characters;}
    // 【解释】读取项目 ACTIVE 角色的知识定义，过滤已失效角色。
    public List<CharacterKnowledge> definitions(Long p) {
        Set<Long> active=new HashSet<>();
        characters.selectList(new LambdaQueryWrapper<CharacterProfile>().eq(CharacterProfile::getProjectId,p).eq(CharacterProfile::getStatus,"ACTIVE"))
            .forEach(c->active.add(c.getId()));
        return knowledge.selectList(new LambdaQueryWrapper<CharacterKnowledge>().eq(CharacterKnowledge::getProjectId,p).orderByAsc(CharacterKnowledge::getId))
            .stream().filter(k->active.contains(k.getCharacterId())).toList();
    }
    /** Key is characterId:knowledgeKey. Null acquisition node means initially available; visiting a node acquires once. */
    // 【解释】复制知识状态；初始知识或到达获取节点时 putIfAbsent，已获得知识不会被重复覆盖。
    // 【修改提示】改变获取规则会同时影响初始化、推进和发布快照模拟，不能只改前端知识展示。
    public Map<String,String> enter(List<CharacterKnowledge> definitions,Map<String,String> before,Long node,boolean initial) {
        Map<String,String> after=new LinkedHashMap<>(before);
        for(var k:definitions) if((initial && k.getAcquiredNodeId()==null) || node.equals(k.getAcquiredNodeId()))
            after.putIfAbsent(k.getCharacterId()+":"+k.getKnowledgeKey(),k.getKnowledgeLevel());
        return after;
    }
}
