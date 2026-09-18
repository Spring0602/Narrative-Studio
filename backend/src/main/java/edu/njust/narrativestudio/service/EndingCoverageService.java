package edu.njust.narrativestudio.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.njust.narrativestudio.entity.StoryNode;
import edu.njust.narrativestudio.mapper.StoryNodeMapper;
import java.util.*;
import edu.njust.narrativestudio.mapper.EndingCoverageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EndingCoverageService {
    public record Ending(Long nodeId,String nodeKey,String title,long completions) {}
    public record Coverage(Long releaseId,long totalSessions,long completedSessions,long runningSessions,
            long abortedSessions,int totalEndings,long reachedEndings,double coveragePercent,List<Ending> endings) {}
    private final ProjectAccessService access;
    private final ReleaseService releases;
    private final StoryNodeMapper nodes;
    private final EndingCoverageMapper counts;
    public EndingCoverageService(ProjectAccessService access,ReleaseService releases,StoryNodeMapper nodes,EndingCoverageMapper counts) {
        this.access=access;this.releases=releases;this.nodes=nodes;this.counts=counts;
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Coverage get(Long user,Long project,Long release) {
        access.requireMember(user,project);
        var definitions=release==null
                ? nodes.selectList(new LambdaQueryWrapper<StoryNode>().eq(StoryNode::getProjectId,project).orderByAsc(StoryNode::getId))
                : releases.get(user,project,release).nodes();
        Map<String,Long> statuses=new HashMap<>();
        Map<Long,Long> reached=new HashMap<>();
        for(var row:counts.counts(user,project,release)) {
            statuses.merge(row.status(),row.amount(),Long::sum);
            if("COMPLETED".equals(row.status())) reached.merge(row.currentNodeId(),row.amount(),Long::sum);
        }
        var endings=definitions.stream().filter(n->"ENDING".equals(n.getNodeType()))
                .map(n->new Ending(n.getId(),n.getNodeKey(),n.getTitle(),reached.getOrDefault(n.getId(),0L))).toList();
        long covered=endings.stream().filter(e->e.completions()>0).count();
        return new Coverage(release,statuses.values().stream().mapToLong(Long::longValue).sum(),
                statuses.getOrDefault("COMPLETED",0L),statuses.getOrDefault("RUNNING",0L),statuses.getOrDefault("ABORTED",0L),
                endings.size(),covered,endings.isEmpty()?0:Math.round(covered*10000.0/endings.size())/100.0,endings);
    }
}
