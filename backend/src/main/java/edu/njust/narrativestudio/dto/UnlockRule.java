package edu.njust.narrativestudio.dto;
import java.util.List;
/** Bounded declarative AST. Never evaluated as scripts; keys are portable across imports. */
public record UnlockRule(String type,List<UnlockRule> children,Integer count,
                         String nodeKey,String variableKey,String operator,String value) {}
