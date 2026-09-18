package edu.njust.narrativestudio.engine;

import static org.junit.jupiter.api.Assertions.*;
import edu.njust.narrativestudio.entity.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

class GraphAnalyzerTest {
    StoryNode node(long id) {var n=new StoryNode();n.setId(id);n.setNodeType("NORMAL");n.setIsStart(id==1);return n;}
    StoryChoice edge(long from,long to,boolean enabled) {
        var c=new StoryChoice();c.setId(from*1000+to);c.setSourceNodeId(from);c.setTargetNodeId(to);c.setEnabled(enabled);return c;
    }
    @Test void marksOnlyCycleMembersIncludingSelfLoops() {
        var ns=List.of(node(1),node(2),node(3),node(4),node(5));
        var cs=List.of(edge(1,2,true),edge(2,1,true),edge(2,3,true),edge(4,4,true),edge(5,5,false));
        var cyclic=new GraphAnalyzer().analyze(ns,cs).stream().filter(f->f.type().equals("CYCLE")).map(GraphAnalyzer.Finding::targetId).toList();
        assertEquals(List.of(1L,2L,4L),cyclic);
    }
    @Test void fullCapacityGraphAnalysisMeetsTwoSecondBudget() {
        List<StoryNode> ns=new ArrayList<>();List<StoryChoice> cs=new ArrayList<>();
        for(int i=1;i<=500;i++) {ns.add(node(i));cs.add(edge(i,i%500+1,true));cs.add(edge(i,(i+7)%500+1,true));}
        assertTimeout(Duration.ofSeconds(2),()->{
            long start=System.nanoTime();
            assertEquals(500,new GraphAnalyzer().analyze(ns,cs).stream().filter(f->f.type().equals("CYCLE")).count());
            System.out.printf("GraphAnalyzer 500 nodes / 1000 edges: %.2f ms%n",(System.nanoTime()-start)/1e6);
        });
    }
}
