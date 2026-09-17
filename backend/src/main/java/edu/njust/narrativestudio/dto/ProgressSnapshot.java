package edu.njust.narrativestudio.dto;
import java.util.*;
public record ProgressSnapshot(Map<String,String> values,Set<String> completedEndings,Set<String> visitedNodes) {
    public ProgressSnapshot {
        values=Collections.unmodifiableMap(new LinkedHashMap<>(values));
        completedEndings=Collections.unmodifiableSet(new LinkedHashSet<>(completedEndings));
        visitedNodes=Collections.unmodifiableSet(new LinkedHashSet<>(visitedNodes));
    }
    public static ProgressSnapshot empty() {return new ProgressSnapshot(Map.of(),Set.of(),Set.of());}
}
