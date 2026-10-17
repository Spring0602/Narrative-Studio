package edu.njust.narrativestudio.dto;
import edu.njust.narrativestudio.entity.*;
import java.util.*;
/** Version 1: immutable serialized authoring data. No users, credentials or playtest history. */
// 【发布快照】保存发布时创作数据，供指定版本模拟使用；node() 在快照内按 ID 找节点，找不到则报损坏/缺失。
// 【修改提示】新增快照字段需兼容历史发布版本，不得回退查询当前编辑稿来假装补全旧版本。
public record ReleaseSnapshot(NarrativeProject project,List<StoryNode> nodes,List<StoryChoice> choices,
        List<StateVariable> variables,List<ChoiceCondition> conditions,List<StateEffect> effects,
        List<WorldEntry> worldEntries,List<CharacterProfile> characters,List<CharacterRelation> relations,
        List<CharacterKnowledge> knowledge,Map<Long,List<Long>> cast) {
    public StoryNode node(Long id) {
        return nodes.stream().filter(n->id.equals(n.getId())).findFirst()
            .orElseThrow(()->edu.njust.narrativestudio.exception.BusinessException.conflict("发布版本中缺少节点"));
    }
}
