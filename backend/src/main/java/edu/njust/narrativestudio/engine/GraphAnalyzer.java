package edu.njust.narrativestudio.engine;
import edu.njust.narrativestudio.entity.*;
import java.util.*;
import org.springframework.stereotype.Component;
/** Structural analysis ignores rule satisfiability; disabled choices are excluded. */
@Component
public class GraphAnalyzer {
    public record Finding(String type,String severity,String targetType,Long targetId,String message) {}
    public List<Finding> analyze(List<StoryNode> nodes,List<StoryChoice> choices) {
        Map<Long,Set<Long>> edges=new LinkedHashMap<>();Set<Long> incoming=new HashSet<>();
        nodes.forEach(n->edges.put(n.getId(),new LinkedHashSet<>()));
        List<Finding> result=new ArrayList<>();
        for(StoryChoice c:choices) if(Boolean.TRUE.equals(c.getEnabled())) {
            if(!edges.containsKey(c.getSourceNodeId()) || !edges.containsKey(c.getTargetNodeId())) {
                result.add(new Finding("BROKEN_REFERENCE","ERROR","CHOICE",c.getId(),"选择引用了图外节点"));continue;
            }
            edges.get(c.getSourceNodeId()).add(c.getTargetNodeId());incoming.add(c.getTargetNodeId());
        }
        List<Long> starts=nodes.stream().filter(n->Boolean.TRUE.equals(n.getIsStart())).map(StoryNode::getId).toList();
        if(starts.size()!=1) result.add(new Finding("START_COUNT","ERROR","PROJECT",null,"剧情必须有且只有一个起点"));
        Set<Long> reached=reachable(starts,edges);
        Set<Long> cyclic=new StrongComponents(edges).cyclic;
        for(StoryNode n:nodes) {
            Long id=n.getId();Set<Long> out=edges.get(id);
            if(out.isEmpty() && !incoming.contains(id)) result.add(f("ISOLATED","WARNING",id,"节点没有启用的连接"));
            if(!reached.contains(id)) result.add(f("UNREACHABLE","ERROR",id,"节点无法从起点到达"));
            if(!"ENDING".equals(n.getNodeType()) && out.isEmpty()) result.add(f("DEAD_END","ERROR",id,"普通节点没有启用的出边"));
            if("ENDING".equals(n.getNodeType()) && !out.isEmpty()) result.add(f("ENDING_OUTGOING","ERROR",id,"结局节点不应存在启用的出边"));
            if(cyclic.contains(id)) result.add(f("CYCLE","WARNING",id,"节点位于循环路径中，请确认退出条件"));
        }
        return result;
    }
    private Finding f(String type,String severity,Long id,String message) { return new Finding(type,severity,"NODE",id,message); }
    /** Tarjan SCC marks actual cycle members, not merely nodes downstream of cycles. */
    private static final class StrongComponents {
        final Map<Long,Set<Long>> edges;
        final Map<Long,Integer> index=new HashMap<>(), low=new HashMap<>();
        final Deque<Long> stack=new ArrayDeque<>();
        final Set<Long> onStack=new HashSet<>(), cyclic=new HashSet<>();
        int next;
        StrongComponents(Map<Long,Set<Long>> edges) {
            this.edges=edges;
            for(Long id:edges.keySet()) if(!index.containsKey(id)) visit(id);
        }
        void visit(Long id) {
            index.put(id,next);low.put(id,next++);stack.push(id);onStack.add(id);
            for(Long target:edges.get(id)) {
                if(!index.containsKey(target)) {visit(target);low.put(id,Math.min(low.get(id),low.get(target)));}
                else if(onStack.contains(target)) low.put(id,Math.min(low.get(id),index.get(target)));
            }
            if(low.get(id).equals(index.get(id))) {
                List<Long> component=new ArrayList<>();Long member;
                do {member=stack.pop();onStack.remove(member);component.add(member);} while(!member.equals(id));
                if(component.size()>1 || edges.get(id).contains(id)) cyclic.addAll(component);
            }
        }
    }
    private Set<Long> reachable(Collection<Long> starts,Map<Long,Set<Long>> edges) {
        Set<Long> seen=new HashSet<>();Deque<Long> queue=new ArrayDeque<>(starts);
        while(!queue.isEmpty()) { Long id=queue.removeFirst();if(seen.add(id)) queue.addAll(edges.getOrDefault(id,Set.of())); }
        return seen;
    }
}
