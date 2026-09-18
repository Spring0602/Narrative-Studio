package edu.njust.narrativestudio.engine;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.njust.narrativestudio.dto.*;
import edu.njust.narrativestudio.entity.*;
import edu.njust.narrativestudio.exception.BusinessException;
import java.util.*;
import org.junit.jupiter.api.Test;
class UnlockRuleEngineTest {
    final UnlockRuleEngine engine=new UnlockRuleEngine(new ObjectMapper(),new RuleEngine());
    final List<StoryNode> nodes=new ArrayList<>();
    final List<StateVariable> vars=new ArrayList<>();
    UnlockRuleEngineTest() {
        var node=new StoryNode();node.setId(1L);node.setNodeKey("end");node.setNodeType("ENDING");nodes.add(node);
        var v=new StateVariable();v.setId(2L);v.setVariableKey("flag");v.setValueType("BOOLEAN");v.setInitialValue("false");vars.add(v);
    }
    UnlockRule leaf() {return new UnlockRule("ENDING",null,null,"end",null,null,null);}
    void invalid(UnlockRule rule) {assertThrows(BusinessException.class,()->engine.validate(rule,nodes,vars));}
    @Test void rejectsUnknownTypeAndEmptyGroupsAndInvalidNot() {
        invalid(new UnlockRule("SCRIPT",null,null,null,null,null,"alert(1)"));
        invalid(new UnlockRule("ALL",List.of(),null,null,null,null,null));
        invalid(new UnlockRule("NOT",List.of(leaf(),leaf()),null,null,null,null,null));
    }
    @Test void rejectsInvalidTypedValuesAndForeignVariable() {
        invalid(new UnlockRule("VARIABLE",null,null,null,"flag","GTE","true"));
        invalid(new UnlockRule("VARIABLE",null,null,null,"flag","EQ","TRUE"));
        invalid(new UnlockRule("VARIABLE",null,null,null,"foreign","EQ","true"));
    }
    @Test void duplicateThresholdCannotBypassUsingEmptyChildrenOrUnusedFields() {
        invalid(new UnlockRule("AT_LEAST",List.of(leaf(),new UnlockRule("ENDING",List.of(),null,"end",null,null,null)),2,null,null,null,null));
        invalid(new UnlockRule("AT_LEAST",List.of(leaf(),new UnlockRule("ENDING",null,1,"end",null,null,null)),2,null,null,null,null));
    }
    @Test void rejectsNullChildrenAndOverSizeTrees() {
        invalid(new UnlockRule("ALL",Arrays.asList((UnlockRule)null),null,null,null,null,null));
        var many=new UnlockRule("ALL",Collections.nCopies(32,leaf()),null,null,null,null,null);
        invalid(new UnlockRule("ALL",List.of(many,many,many,many),null,null,null,null,null));
    }
    @Test void allowsEightLevelsButRejectsNine() {
        UnlockRule r=leaf();
        for(int i=0;i<7;i++)r=new UnlockRule("NOT",List.of(r),null,null,null,null,null);
        engine.validate(r,nodes,vars);
        invalid(new UnlockRule("NOT",List.of(r),null,null,null,null,null));
    }
    @Test void absenceIsUnrestrictedAndEvaluationDoesNotMutateProgress() {
        var p=new ProgressSnapshot(Map.of(),Set.of("end"),Set.of("end"));
        var not=new UnlockRule("NOT",List.of(leaf()),null,null,null,null,null);
        assertTrue(engine.available(null,vars,Map.of(),p));assertFalse(engine.available(not,vars,Map.of(),p));
        assertEquals(Set.of("end"),p.completedEndings());
    }
}
