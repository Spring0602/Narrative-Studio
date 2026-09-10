package edu.njust.narrativestudio.engine;
import static org.junit.jupiter.api.Assertions.*;
import edu.njust.narrativestudio.entity.*;
import edu.njust.narrativestudio.exception.BusinessException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RuleEngineTest {
    private final RuleEngine engine=new RuleEngine();
    @ParameterizedTest
    @CsvSource({"BOOLEAN,true","BOOLEAN,false","INTEGER,0","INTEGER,-1","INTEGER,9223372036854775807",
            "INTEGER,-9223372036854775808","STRING,hello","STRING,中文"})
    void acceptsTypedValues(String type,String value) { assertEquals(value,engine.validateValue(type,value)); }
    @ParameterizedTest
    @CsvSource({"BOOLEAN,TRUE","BOOLEAN,False","BOOLEAN,1","BOOLEAN,yes","INTEGER,1.5","INTEGER,abc",
            "INTEGER,01","INTEGER,+1","INTEGER,1e2","INTEGER,9223372036854775808","INTEGER,-9223372036854775809","FLOAT,1"})
    void rejectsMalformedValues(String type,String value) {
        assertEquals("RULE_VALUE_INVALID",assertThrows(BusinessException.class,()->engine.validateValue(type,value)).getCode());
    }
    @Test void rejectsNullValue() { assertThrows(BusinessException.class,()->engine.validateValue("STRING",null)); }
    @Test void rejectsPaddedInteger() { assertThrows(BusinessException.class,()->engine.validateValue("INTEGER"," 1 ")); }
    @Test void acceptsEmptyStringWithoutTrimming() { assertEquals("",engine.validateValue("STRING","")); assertEquals(" a ",engine.validateValue("STRING"," a ")); }
    @Test void rejectsOversizedString() { assertThrows(BusinessException.class,()->engine.validateValue("STRING","x".repeat(501))); }
    @ParameterizedTest
    @CsvSource({"EQ,30,true","EQ,31,false","NE,31,true","NE,30,false","GT,29,true","GT,30,false",
            "GTE,30,true","GTE,31,false","LT,31,true","LT,30,false","LTE,30,true","LTE,29,false"})
    void integerComparison(String op,String expected,boolean result) {
        assertEquals(result,engine.available(List.of(variable("INTEGER","30")),List.of(condition(op,expected,0)),Map.of("trust","30")));
    }
    @Test void noConditionsMeansAvailable() { assertTrue(engine.available(List.of(),List.of(),Map.of())); }
    @Test void andWithinGroup() { assertFalse(engine.available(List.of(variable("INTEGER","30")),List.of(condition("GTE","20",0),condition("LT","25",0)),Map.of("trust","30"))); }
    @Test void orBetweenGroups() { assertTrue(engine.available(List.of(variable("INTEGER","30")),List.of(condition("EQ","50",0),condition("EQ","30",1)),Map.of("trust","30"))); }
    @Test void rejectsMissingVariableEvenAfterTrueGroup() {
        ChoiceCondition foreign=condition("EQ","30",1); foreign.setVariableId(2L);
        assertThrows(BusinessException.class,()->engine.available(List.of(variable("INTEGER","30")),List.of(condition("EQ","30",0),foreign),Map.of("trust","30")));
    }
    @Test void rejectsMissingState() { assertThrows(BusinessException.class,()->engine.available(List.of(variable("INTEGER","30")),List.of(condition("EQ","30",0)),Map.of())); }
    @ParameterizedTest @CsvSource({"BOOLEAN,GT,true","STRING,LT,hello"})
    void rejectsNonNumericOrdering(String type,String op,String value) {
        assertThrows(BusinessException.class,()->engine.validateCondition(variable(type,value),condition(op,value,0)));
    }
    @Test void booleanEquality() { assertTrue(engine.available(List.of(variable("BOOLEAN","true")),List.of(condition("EQ","true",0)),Map.of("trust","true"))); }
    @Test void stringIsCaseSensitive() { assertFalse(engine.available(List.of(variable("STRING","a")),List.of(condition("EQ","A",0)),Map.of("trust","a"))); }
    @Test void conditionsRequireNonnegativeGroup() { assertThrows(BusinessException.class,()->engine.validateCondition(variable("INTEGER","1"),condition("EQ","1",-1))); }
    @ParameterizedTest @CsvSource({"SET,20,20","ADD,20,50","SUBTRACT,20,10","ADD,-40,-10","SUBTRACT,-10,40"})
    void integerEffect(String op,String operand,String expected) {
        assertEquals(expected,engine.apply(List.of(variable("INTEGER","30")),List.of(effect(op,operand,0)),Map.of("trust","30")).get("trust"));
    }
    @Test void effectsAreSequentialAndInputIsImmutable() {
        var before=new HashMap<>(Map.of("trust","30"));
        var after=engine.apply(List.of(variable("INTEGER","30")),List.of(effect("ADD","5",1),effect("SET","10",0)),before);
        assertEquals("15",after.get("trust")); assertEquals("30",before.get("trust"));
    }
    @Test void detectsAdditionOverflowWithoutMutatingInput() {
        var before=new HashMap<>(Map.of("trust",Long.toString(Long.MAX_VALUE)));
        assertThrows(BusinessException.class,()->engine.apply(List.of(variable("INTEGER","0")),List.of(effect("ADD","1",0)),before));
        assertEquals(Long.toString(Long.MAX_VALUE),before.get("trust"));
    }
    @Test void detectsSubtractionOverflow() { assertThrows(BusinessException.class,()->engine.apply(List.of(variable("INTEGER","0")),List.of(effect("SUBTRACT","1",0)),Map.of("trust",Long.toString(Long.MIN_VALUE)))); }
    @ParameterizedTest @CsvSource({"BOOLEAN,ADD,true","STRING,SUBTRACT,x","INTEGER,MULTIPLY,2"})
    void rejectsInvalidEffectOperator(String type,String op,String val) { assertThrows(BusinessException.class,()->engine.validateEffect(variable(type,val),effect(op,val,0))); }
    @Test void setsBoolean() { assertEquals("false",engine.apply(List.of(variable("BOOLEAN","true")),List.of(effect("SET","false",0)),Map.of("trust","true")).get("trust")); }
    @Test void setsString() { assertEquals("",engine.apply(List.of(variable("STRING","x")),List.of(effect("SET","",0)),Map.of("trust","x")).get("trust")); }
    @Test void initialStateValidatesAllVariables() { assertEquals(Map.of("trust","30"),engine.initialState(List.of(variable("INTEGER","30")))); }
    @Test void duplicateKeysRejected() { assertThrows(BusinessException.class,()->engine.initialState(List.of(variable("INTEGER","30"),variable("INTEGER","1")))); }
    @Test void effectCannotReferenceOtherProject() { StateEffect e=effect("SET","1",0);e.setVariableId(99L);assertThrows(BusinessException.class,()->engine.apply(List.of(variable("INTEGER","30")),List.of(e),Map.of("trust","30"))); }
    private StateVariable variable(String type,String value) {
        StateVariable v=new StateVariable();v.setId(1L);v.setVariableKey("trust");v.setValueType(type);v.setInitialValue(value);return v;
    }
    private ChoiceCondition condition(String op,String value,int group) {
        ChoiceCondition c=new ChoiceCondition();c.setVariableId(1L);c.setOperator(op);c.setExpectedValue(value);c.setConditionGroup(group);return c;
    }
    private StateEffect effect(String op,String value,int sort) {
        StateEffect e=new StateEffect();e.setVariableId(1L);e.setOperation(op);e.setOperandValue(value);e.setSortOrder(sort);return e;
    }
}
