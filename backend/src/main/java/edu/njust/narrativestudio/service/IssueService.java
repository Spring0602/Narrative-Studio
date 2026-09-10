package edu.njust.narrativestudio.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import edu.njust.narrativestudio.dto.DatabaseDtos.*;
import edu.njust.narrativestudio.entity.*;
import edu.njust.narrativestudio.mapper.*;
import edu.njust.narrativestudio.engine.GraphAnalyzer;
import edu.njust.narrativestudio.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class IssueService {
    private final DetectedIssueMapper issues;private final StoryNodeMapper nodes;private final StoryChoiceMapper choices;
    private final ProjectAccessService access;private final ProjectMutationGuard guard;private final GraphAnalyzer analyzer;
    public IssueService(DetectedIssueMapper issues,StoryNodeMapper nodes,StoryChoiceMapper choices,ProjectAccessService access,ProjectMutationGuard guard,GraphAnalyzer analyzer) {
        this.issues=issues;this.nodes=nodes;this.choices=choices;this.access=access;this.guard=guard;this.analyzer=analyzer;
    }
    public PlaytestService.Page<DetectedIssue> list(Long u,Long p,int page,int size) {
        access.requireMember(u,p);String limit=FeatureScope.limit(page,size);
        var q=new LambdaQueryWrapper<DetectedIssue>().eq(DetectedIssue::getProjectId,p);long total=issues.selectCount(q);
        return new PlaytestService.Page<>(issues.selectList(q.orderByAsc(DetectedIssue::getId).last(limit)),page,size,total,(total+size-1)/size);
    }
    public DetectedIssue get(Long u,Long p,Long id) { access.requireMember(u,p);return require(p,id); }
    @Transactional
    public DetectedIssue status(Long u,Long p,Long id,StatusRequest r) {
        guard.editor(u,p);DetectedIssue i=require(p,id);setStatus(i,r.status());return i;
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public List<DetectedIssue> analyze(Long u,Long p) {
        guard.editor(u,p);
        var ns=nodes.selectList(new LambdaQueryWrapper<StoryNode>().eq(StoryNode::getProjectId,p).orderByAsc(StoryNode::getId));
        if(ns.size()>500) throw FeatureScope.invalid("结构检测最多支持500个节点");
        var cs=choices.selectList(new LambdaQueryWrapper<StoryChoice>().eq(StoryChoice::getProjectId,p).orderByAsc(StoryChoice::getId));
        var old=issues.selectList(new LambdaQueryWrapper<DetectedIssue>().eq(DetectedIssue::getProjectId,p));
        Map<String,DetectedIssue> indexed=new HashMap<>();old.forEach(i->indexed.put(key(i.getIssueType(),i.getTargetType(),i.getTargetId()),i));
        List<DetectedIssue> active=new ArrayList<>();Set<Long> present=new HashSet<>();
        for(var f:analyzer.analyze(ns,cs)) {
            DetectedIssue i=indexed.get(key(f.type(),f.targetType(),f.targetId()));
            if(i==null) {
                i=new DetectedIssue();i.setProjectId(p);i.setIssueType(f.type());i.setSeverity(f.severity());i.setTargetType(f.targetType());
                i.setTargetId(f.targetId());i.setMessage(f.message());i.setStatus("OPEN");i.setDetectedAt(LocalDateTime.now());issues.insert(i);
            } else if("RESOLVED".equals(i.getStatus())) setStatus(i,"OPEN");
            active.add(i);present.add(i.getId());
        }
        for(DetectedIssue i:old) if(!present.contains(i.getId()) && "OPEN".equals(i.getStatus())) setStatus(i,"RESOLVED");
        return active;
    }
    private String key(String type,String target,Long id) { return type+":"+target+":"+id; }
    private void setStatus(DetectedIssue i,String status) {
        i.setStatus(status);i.setResolvedAt("RESOLVED".equals(status)?LocalDateTime.now():null);
        issues.update(null,new LambdaUpdateWrapper<DetectedIssue>().eq(DetectedIssue::getId,i.getId())
            .set(DetectedIssue::getStatus,status).set(DetectedIssue::getResolvedAt,i.getResolvedAt()));
    }
    private DetectedIssue require(Long p,Long id) {
        DetectedIssue i=issues.selectById(id);if(i==null || !p.equals(i.getProjectId())) throw BusinessException.notFound("检测问题不存在");return i;
    }
}
