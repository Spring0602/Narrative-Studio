package edu.njust.narrativestudio.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.njust.narrativestudio.dto.PlaytestDtos.*;
import edu.njust.narrativestudio.engine.RuleEngine;
import edu.njust.narrativestudio.entity.*;
import edu.njust.narrativestudio.exception.BusinessException;
import edu.njust.narrativestudio.mapper.*;
import edu.njust.narrativestudio.service.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class PlaytestServiceImpl implements PlaytestService {
    private final PlaytestSessionMapper sessions;
    private final PlaytestStepMapper steps;
    private final StoryNodeMapper nodes;
    private final StoryChoiceMapper choices;
    private final ProjectAccessService access;
    private final ProjectMutationGuard guard;
    private final RuleCatalog catalog;
    private final RuleEngine engine;
    private final ObjectMapper json;
    private final ReleaseService releases;
    private final KnowledgeRuntime knowledge;
    public PlaytestServiceImpl(PlaytestSessionMapper sessions,PlaytestStepMapper steps,StoryNodeMapper nodes,
            StoryChoiceMapper choices,ProjectAccessService access,ProjectMutationGuard guard,RuleCatalog catalog,
            RuleEngine engine,ObjectMapper json,ReleaseService releases,KnowledgeRuntime knowledge) {
        this.sessions=sessions; this.steps=steps; this.nodes=nodes; this.choices=choices; this.access=access;
        this.guard=guard; this.catalog=catalog; this.engine=engine; this.json=json;
        this.releases=releases;this.knowledge=knowledge;
    }
    @Transactional
    public SessionView start(Long u,Long p) { guard.member(u,p); return startInternal(u,p,null); }
    @Transactional
    public SessionView startRelease(Long u,Long p,Long release) {
        guard.member(u,p);releases.snapshot(p,release);return startInternal(u,p,release);
    }
    private SessionView startInternal(Long u,Long p,Long release) {
        var frozen=release==null?null:releases.snapshot(p,release);
        List<StoryNode> starts=frozen==null
            ?nodes.selectList(new LambdaQueryWrapper<StoryNode>().eq(StoryNode::getProjectId,p).eq(StoryNode::getIsStart,true))
            :frozen.nodes().stream().filter(n->Boolean.TRUE.equals(n.getIsStart())).toList();
        if(starts.size()!=1) throw invalidState("剧情必须有且只有一个起点");
        StoryNode start=starts.getFirst(); validateNodeType(start);
        Map<String,String> initial=engine.initialState(frozen==null?catalog.variables(p):frozen.variables());
        PlaytestSession s=new PlaytestSession(); s.setProjectId(p); s.setTesterId(u);
        s.setReleaseId(release);
        s.setCurrentNodeId(start.getId()); s.setStartedAt(LocalDateTime.now());
        s.setStatus("ENDING".equals(start.getNodeType()) ? "COMPLETED" : "RUNNING");
        if("COMPLETED".equals(s.getStatus())) s.setFinishedAt(s.getStartedAt());
        sessions.insert(s);
        PlaytestStep step=step(s,0,start.getId(),null,initial,initial);
        var initialKnowledge=knowledge.enter(frozen==null?knowledge.definitions(p):frozen.knowledge(),Map.of(),start.getId(),true);
        step.setKnowledgeBefore(encode(initialKnowledge));step.setKnowledgeAfter(encode(initialKnowledge));steps.insert(step);
        return view(s,start,step);
    }
    public SessionView get(Long u,Long p,Long id) {
        access.requireMember(u,p); PlaytestSession s=owned(u,p,id); return view(s,node(s,s.getCurrentNodeId()),latest(id));
    }
    @Transactional
    public SessionView advance(Long u,Long p,Long id,Long choiceId,int expectedStepNo) {
        guard.member(u,p); PlaytestSession s=owned(u,p,id);
        if(!"RUNNING".equals(s.getStatus())) throw invalidState("会话已结束");
        PlaytestStep previous=latest(id);
        if(previous.getStepNo()!=expectedStepNo) throw invalidState("会话进度已变化，请刷新后重试");
        if(previous.getStepNo()>=10000) throw invalidState("会话已达到10000步上限，请重新开始");
        StoryNode current=node(s,s.getCurrentNodeId());
        if("ENDING".equals(current.getNodeType())) throw invalidState("当前节点已是结局");
        var frozen=s.getReleaseId()==null?null:releases.snapshot(p,s.getReleaseId());
        StoryChoice c=frozen==null?choices.selectById(choiceId):frozen.choices().stream().filter(item->choiceId.equals(item.getId())).findFirst().orElse(null);
        if(c==null || !p.equals(c.getProjectId()) || !s.getCurrentNodeId().equals(c.getSourceNodeId())) throw BusinessException.notFound("当前节点不存在该选择");
        if(!Boolean.TRUE.equals(c.getEnabled())) throw invalidState("选择已停用");
        List<StateVariable> vars=frozen==null?catalog.variables(p):frozen.variables();
        Map<String,String> before=decode(previous.getStateAfter());
        var conditions=frozen==null?catalog.conditions(choiceId):frozen.conditions().stream().filter(item->choiceId.equals(item.getChoiceId())).toList();
        var effects=frozen==null?catalog.effects(choiceId):frozen.effects().stream().filter(item->choiceId.equals(item.getChoiceId())).toList();
        if(!engine.available(vars,conditions,before)) throw invalidState("当前状态不满足选择条件");
        StoryNode target=node(s,c.getTargetNodeId());
        Map<String,String> after=engine.apply(vars,effects,before);
        PlaytestStep next=step(s,previous.getStepNo()+1,target.getId(),choiceId,before,after);
        var knowledgeBefore=decodeKnowledge(previous.getKnowledgeAfter());
        next.setKnowledgeBefore(encode(knowledgeBefore));
        next.setKnowledgeAfter(encode(knowledge.enter(frozen==null?knowledge.definitions(p):frozen.knowledge(),knowledgeBefore,target.getId(),false)));
        steps.insert(next);
        s.setCurrentNodeId(target.getId());
        if("ENDING".equals(target.getNodeType())) { s.setStatus("COMPLETED"); s.setFinishedAt(LocalDateTime.now()); }
        sessions.updateById(s);
        return view(s,target,next);
    }
    @Transactional
    public SessionView restart(Long u,Long p,Long id) {
        guard.member(u,p); PlaytestSession old=owned(u,p,id);
        if("RUNNING".equals(old.getStatus())) { old.setStatus("ABORTED"); old.setFinishedAt(LocalDateTime.now()); sessions.updateById(old); }
        return startInternal(u,p,old.getReleaseId()); // Published sessions restart on the same immutable version.
    }
    @Transactional
    public SessionView stop(Long u,Long p,Long id) {
        guard.member(u,p); PlaytestSession s=owned(u,p,id);
        if("RUNNING".equals(s.getStatus())) { s.setStatus("ABORTED"); s.setFinishedAt(LocalDateTime.now()); sessions.updateById(s); }
        return view(s,node(s,s.getCurrentNodeId()),latest(id));
    }
    public Page<SessionView> list(Long u,Long p,int page,int size) {
        access.requireMember(u,p); paging(page,size);
        var q=new LambdaQueryWrapper<PlaytestSession>().eq(PlaytestSession::getProjectId,p).eq(PlaytestSession::getTesterId,u);
        long total=sessions.selectCount(q);
        var rows=sessions.selectList(q.orderByDesc(PlaytestSession::getId).last(limit(page,size)));
        return new Page<>(rows.stream().map(s->view(s,node(s,s.getCurrentNodeId()),latest(s.getId()))).toList(),page,size,total,(total+size-1)/size);
    }
    public Page<StepView> steps(Long u,Long p,Long id,int page,int size) {
        access.requireMember(u,p); owned(u,p,id); paging(page,size);
        var q=new LambdaQueryWrapper<PlaytestStep>().eq(PlaytestStep::getSessionId,id);
        long total=steps.selectCount(q);
        var rows=steps.selectList(q.orderByAsc(PlaytestStep::getStepNo).last(limit(page,size)));
        return new Page<>(rows.stream().map(s->new StepView(s.getId(),s.getStepNo(),s.getNodeId(),s.getChoiceId(),
                decode(s.getStateBefore()),decode(s.getStateAfter()),s.getCreatedAt(),
                decodeKnowledge(s.getKnowledgeBefore()),decodeKnowledge(s.getKnowledgeAfter()))).toList(),page,size,total,(total+size-1)/size);
    }
    private SessionView view(PlaytestSession s,StoryNode current,PlaytestStep latest) {
        Map<String,String> state=decode(latest.getStateAfter());
        List<ChoiceView> visible=new ArrayList<>();
        if("RUNNING".equals(s.getStatus()) && !"ENDING".equals(current.getNodeType())) {
            var frozen=s.getReleaseId()==null?null:releases.snapshot(s.getProjectId(),s.getReleaseId());
            List<StateVariable> vars=frozen==null?catalog.variables(s.getProjectId()):frozen.variables();
            var candidates=frozen==null?choices.selectList(new LambdaQueryWrapper<StoryChoice>().eq(StoryChoice::getProjectId,s.getProjectId())
                    .eq(StoryChoice::getSourceNodeId,current.getId()).eq(StoryChoice::getEnabled,true)
                    .orderByAsc(StoryChoice::getSortOrder).orderByAsc(StoryChoice::getId))
                :frozen.choices().stream().filter(c->current.getId().equals(c.getSourceNodeId()) && Boolean.TRUE.equals(c.getEnabled())).toList();
            for(StoryChoice c:candidates) {
                var conditions=frozen==null?catalog.conditions(c.getId()):frozen.conditions().stream().filter(item->c.getId().equals(item.getChoiceId())).toList();
                if(engine.available(vars,conditions,state))
                    visible.add(new ChoiceView(c.getId(),c.getTargetNodeId(),c.getChoiceText(),c.getSortOrder()));
            }
        }
        return new SessionView(s.getId(),s.getProjectId(),s.getTesterId(),s.getStatus(),
                new NodeView(current.getId(),current.getNodeKey(),current.getTitle(),current.getContent(),current.getNodeType()),
                latest.getStepNo(),Collections.unmodifiableMap(state),visible,
                "RUNNING".equals(s.getStatus()) && visible.isEmpty(),s.getStartedAt(),s.getFinishedAt(),
                s.getReleaseId(),decodeKnowledge(latest.getKnowledgeAfter()));
    }
    private PlaytestSession owned(Long u,Long p,Long id) {
        PlaytestSession s=sessions.selectById(id);
        if(s==null || !p.equals(s.getProjectId())) throw BusinessException.notFound("会话不存在");
        if(!u.equals(s.getTesterId())) throw BusinessException.forbidden("只能操作和回放自己的模拟会话");
        return s;
    }
    private StoryNode node(Long p,Long id) {
        StoryNode n=nodes.selectById(id);
        if(n==null || !p.equals(n.getProjectId())) throw BusinessException.notFound("剧情节点不存在");
        validateNodeType(n); return n;
    }
    private void validateNodeType(StoryNode n) {
        // Both live and frozen nodes use the same runtime vocabulary.
        if(!Set.of("NORMAL","ENDING").contains(n.getNodeType()==null ? "" : n.getNodeType())) throw invalidState("节点类型不合法");
    }
    private StoryNode node(PlaytestSession session,Long id) {
        return session.getReleaseId()==null?node(session.getProjectId(),id):releases.snapshot(session.getProjectId(),session.getReleaseId()).node(id);
    }
    private Map<String,String> decodeKnowledge(String text) { return text==null?Map.of():decode(text); }
    private PlaytestStep latest(Long id) {
        PlaytestStep step=steps.selectOne(new LambdaQueryWrapper<PlaytestStep>().eq(PlaytestStep::getSessionId,id)
                .orderByDesc(PlaytestStep::getStepNo).last("LIMIT 1"));
        if(step==null) throw invalidState("会话缺少初始快照"); return step;
    }
    private PlaytestStep step(PlaytestSession s,int no,Long node,Long choice,Map<String,String> before,Map<String,String> after) {
        PlaytestStep step=new PlaytestStep(); step.setSessionId(s.getId()); step.setStepNo(no);
        step.setNodeId(node); step.setChoiceId(choice); step.setStateBefore(encode(before)); step.setStateAfter(encode(after));
        step.setCreatedAt(LocalDateTime.now()); return step;
    }
    private String encode(Map<String,String> state) {
        try { return json.writeValueAsString(state); }
        catch(JsonProcessingException e) { throw invalidState("状态快照无法序列化"); }
    }
    private Map<String,String> decode(String text) {
        try {
            Map<String,String> result=json.readValue(text,new TypeReference<LinkedHashMap<String,String>>() {});
            if(result==null || result.values().stream().anyMatch(Objects::isNull)) throw invalidState("状态快照损坏");
            return result;
        } catch(JsonProcessingException | IllegalArgumentException e) { throw invalidState("状态快照损坏"); }
    }
    private void paging(int page,int size) {
        if(page<1 || size<1 || size>100) throw new BusinessException("VALIDATION_ERROR","page至少为1，size范围为1至100",HttpStatus.BAD_REQUEST);
    }
    private String limit(int page,int size) { return "LIMIT "+size+" OFFSET "+((long)(page-1)*size); }
    private BusinessException invalidState(String message) { return new BusinessException("INVALID_STATE",message,HttpStatus.CONFLICT); }
}
