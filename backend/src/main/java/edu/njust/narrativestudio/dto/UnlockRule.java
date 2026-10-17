package edu.njust.narrativestudio.dto;
import java.util.List;
/** Bounded declarative AST. Never evaluated as scripts; keys are portable across imports. */
// 【递归规则树】组合节点通过 children 包含子条件，叶条件引用节点或变量标识；类型语义由 UnlockRuleEngine 校验与执行。
// 【修改提示】新增字段/类型需同时处理 JSON 兼容、校验、运行、描述、引用检查、发布/导出快照和前端编辑。
public record UnlockRule(String type,List<UnlockRule> children,Integer count,
                         String nodeKey,String variableKey,String operator,String value) {}
