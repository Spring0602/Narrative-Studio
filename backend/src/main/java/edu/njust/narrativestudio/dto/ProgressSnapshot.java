package edu.njust.narrativestudio.dto;
import java.util.*;
// 【跨局快照】保存 PROFILE 值、已达成结局及访问节点集合；构造器复制并包装为只读集合，empty 返回空进度。
// 【修改提示】不要为追加一个结局直接改只读 Set，应复制后构造新快照；新增字段需兼容旧 JSON。
public record ProgressSnapshot(Map<String,String> values,Set<String> completedEndings,Set<String> visitedNodes) {
    public ProgressSnapshot {
        values=Collections.unmodifiableMap(new LinkedHashMap<>(values));
        completedEndings=Collections.unmodifiableSet(new LinkedHashSet<>(completedEndings));
        visitedNodes=Collections.unmodifiableSet(new LinkedHashSet<>(visitedNodes));
    }
    public static ProgressSnapshot empty() {return new ProgressSnapshot(Map.of(),Set.of(),Set.of());}
}
