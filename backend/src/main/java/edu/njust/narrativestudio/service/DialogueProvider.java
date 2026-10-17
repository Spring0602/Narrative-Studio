package edu.njust.narrativestudio.service;

/** Optional external text generation. Implementations must not persist authoring data. */
// 【AI 扩展接口】generate 接收上下文并返回候选文本，当前实现是 OllamaDialogueProvider；配置接口不代表 AI 已启用。
// 【修改提示】替换供应商应实现此接口并明确 Bean 选择、超时/限流/隐私同意；不能将候选自动保存为正式剧情。
public interface DialogueProvider {
    String generate(String context);
}
