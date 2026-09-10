package edu.njust.narrativestudio.dto;
import edu.njust.narrativestudio.entity.*;
import java.util.*;
/** Version 1: immutable serialized authoring data. No users, credentials or playtest history. */
public record ReleaseSnapshot(NarrativeProject project,List<StoryNode> nodes,List<StoryChoice> choices,
        List<StateVariable> variables,List<ChoiceCondition> conditions,List<StateEffect> effects,
        List<WorldEntry> worldEntries,List<CharacterProfile> characters,List<CharacterRelation> relations,
        List<CharacterKnowledge> knowledge,Map<Long,List<Long>> cast) {
    public StoryNode node(Long id) {
        return nodes.stream().filter(n->id.equals(n.getId())).findFirst()
            .orElseThrow(()->edu.njust.narrativestudio.exception.BusinessException.conflict("发布版本中缺少节点"));
    }
}
