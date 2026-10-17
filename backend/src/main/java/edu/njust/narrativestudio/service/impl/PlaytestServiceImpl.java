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
// 答辩修改导航：优先练习 advance 的步数上限、list/steps 的排序、paging 的分页上限、view 的返回字段。
// 注释中的替换代码只是备选练习，不是已启用功能。每次只按题目修改一项，再验证正常和边界场景。
// 核心保护：保留写方法的 @Transactional、guard、owned 和 expectedStepNo 校验。
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
    private final PlayerProgressService progress;
    private final edu.njust.narrativestudio.engine.UnlockRuleEngine unlock;
    public PlaytestServiceImpl(PlaytestSessionMapper sessions,PlaytestStepMapper steps,StoryNodeMapper nodes,
            StoryChoiceMapper choices,ProjectAccessService access,ProjectMutationGuard guard,RuleCatalog catalog,
            RuleEngine engine,ObjectMapper json,ReleaseService releases,KnowledgeRuntime knowledge,PlayerProgressService progress,
            edu.njust.narrativestudio.engine.UnlockRuleEngine unlock) {
        this.sessions=sessions; this.steps=steps; this.nodes=nodes; this.choices=choices; this.access=access;
        this.guard=guard; this.catalog=catalog; this.engine=engine; this.json=json;
        this.releases=releases;this.knowledge=knowledge;
        this.progress=progress;this.unlock=unlock;
    }
    @Transactional
    // 【解释】项目成员加锁后启动编辑稿模拟。
    public SessionView start(Long u,Long p) { guard.member(u,p); return startInternal(u,p,null); }
    @Transactional
    // 【解释】先验证发布快照属于项目，再启动绑定版本的模拟。
    public SessionView startRelease(Long u,Long p,Long release) {
        guard.member(u,p);releases.snapshot(p,release);return startInternal(u,p,release);
    }
    // 【解释】选择编辑稿或发布快照，检查唯一起点，组合初始变量和跨局进度，创建会话与第 0 步并记录知识。
    private SessionView startInternal(Long u,Long p,Long release) {
        var frozen=release==null?null:releases.snapshot(p,release);
        List<StoryNode> starts=frozen==null
            ?nodes.selectList(new LambdaQueryWrapper<StoryNode>().eq(StoryNode::getProjectId,p).eq(StoryNode::getIsStart,true))
            :frozen.nodes().stream().filter(n->Boolean.TRUE.equals(n.getIsStart())).toList();
        if(starts.size()!=1) throw invalidState("剧情必须有且只有一个起点");
        StoryNode start=starts.getFirst(); validateNodeType(start);
        var vars=frozen==null?catalog.variables(p):frozen.variables();
        var progressBefore=progress.snapshot(u,p,release);
        Map<String,String> initial=progress.overlay(vars,engine.initialState(vars),progressBefore);
        PlaytestSession s=new PlaytestSession(); s.setProjectId(p); s.setTesterId(u);
        s.setReleaseId(release);
        s.setCurrentNodeId(start.getId()); s.setStartedAt(LocalDateTime.now());
        s.setStatus("ENDING".equals(start.getNodeType()) ? "COMPLETED" : "RUNNING");
        if("COMPLETED".equals(s.getStatus())) s.setFinishedAt(s.getStartedAt());
        sessions.insert(s);
        PlaytestStep step=step(s,0,start.getId(),null,initial,initial);
        step.setProgressBefore(progress.encode(progressBefore));
        step.setProgressAfter(progress.encode(progress.enter(u,p,release,vars,initial,start,progressBefore)));
        var initialKnowledge=knowledge.enter(frozen==null?knowledge.definitions(p):frozen.knowledge(),Map.of(),start.getId(),true);
        step.setKnowledgeBefore(encode(initialKnowledge));step.setKnowledgeAfter(encode(initialKnowledge));steps.insert(step);
        return view(s,start,step);
    }
    // 【解释】读取本人会话与最新步骤，调用 view 形成前端展示。
    public SessionView get(Long u,Long p,Long id) {
        access.requireMember(u,p); PlaytestSession s=owned(u,p,id); return view(s,node(s,s.getCurrentNodeId()),latest(id));
    }
    @Transactional
    // 【解释】检查会话/步数/选项，准备状态，校验普通条件与解锁树，执行效果，写步骤与进度，更新会话。
    // 事务把这些写入作为整体；before 与 after 分别保留选择前后状态。
    public SessionView advance(Long u,Long p,Long id,Long choiceId,int expectedStepNo) {
        guard.member(u,p); PlaytestSession s=owned(u,p,id);
        if(!"RUNNING".equals(s.getStatus())) throw invalidState("会话已结束");
        PlaytestStep previous=latest(id);
        if(previous.getStepNo()!=expectedStepNo) throw invalidState("会话进度已变化，请刷新后重试");
        // 【常见修改 1：最多推进 5000 步】替换下一行为：
        // if(previous.getStepNo()>=5000) throw invalidState("会话已达到5000步上限，请重新开始");
        // 初始记录为第 0 步；4999 -> 5000 允许，已经是 5000 时再推进拒绝。
        // 数字与提示文字一起改；测试边界，不要为了改上限删除上一行的过期步数检查。
        if(previous.getStepNo()>=10000) throw invalidState("会话已达到10000步上限，请重新开始");
        StoryNode current=node(s,s.getCurrentNodeId());
        if("ENDING".equals(current.getNodeType())) throw invalidState("当前节点已是结局");
        var frozen=s.getReleaseId()==null?null:releases.snapshot(p,s.getReleaseId());
        StoryChoice c=frozen==null?choices.selectById(choiceId):frozen.choices().stream().filter(item->choiceId.equals(item.getId())).findFirst().orElse(null);
        if(c==null || !p.equals(c.getProjectId()) || !s.getCurrentNodeId().equals(c.getSourceNodeId())) throw BusinessException.notFound("当前节点不存在该选择");
        if(!Boolean.TRUE.equals(c.getEnabled())) throw invalidState("选择已停用");
        List<StateVariable> vars=frozen==null?catalog.variables(p):frozen.variables();
        var progressBefore=progress.snapshot(u,p,s.getReleaseId());
        Map<String,String> before=progress.overlay(vars,decode(previous.getStateAfter()),progressBefore);
        var conditions=frozen==null?catalog.conditions(choiceId):frozen.conditions().stream().filter(item->choiceId.equals(item.getChoiceId())).toList();
        var effects=frozen==null?catalog.effects(choiceId):frozen.effects().stream().filter(item->choiceId.equals(item.getChoiceId())).toList();
        // 【常见修改 2：错误提示更明确】可以只替换下面字符串，例如“当前变量未满足选项要求，请检查条件”。
        // 保留判断、异常类型和错误码；invalidState 对应 HTTP 409，改文字不代表改业务规则。
        // 【复杂规则入口，仅理解】engine.available 检查普通条件；unlock.available 检查结构化解锁规则。
        // view() 也调用它们来显示选项；不要只改此处或只改 view，否则显示与实际可选性会不一致。
        if(!engine.available(vars,conditions,before)) throw invalidState("当前状态不满足选择条件");
        if(!unlock.available(unlock.decode(c.getUnlockRule()),vars,before,progressBefore))
            throw invalidState("尚未满足跨路线解锁条件");
        StoryNode target=node(s,c.getTargetNodeId());
        // engine.apply 计算新状态，保留 before 供历史记录使用；不要直接覆盖 before 来省一个变量。
        // 改某个剧情的加减数值通常是改该剧情的效果配置，不是把业务数值硬编码进这个公共方法。
        Map<String,String> after=engine.apply(vars,effects,before);
        PlaytestStep next=step(s,previous.getStepNo()+1,target.getId(),choiceId,before,after);
        next.setProgressBefore(progress.encode(progressBefore));
        next.setProgressAfter(progress.encode(progress.enter(u,p,s.getReleaseId(),vars,after,target,progressBefore)));
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
    // 【解释】旧会话仍运行时标记 ABORTED，再新建一局；发布稿仍绑定原版本，跨局进度不会随重开清空。
    public SessionView restart(Long u,Long p,Long id) {
        guard.member(u,p); PlaytestSession old=owned(u,p,id);
        if("RUNNING".equals(old.getStatus())) { old.setStatus("ABORTED"); old.setFinishedAt(LocalDateTime.now()); sessions.updateById(old); }
        return startInternal(u,p,old.getReleaseId()); // Published sessions restart on the same immutable version.
    }
    @Transactional
    // 【解释】将本人运行中的会话标记 ABORTED 并记录结束时间；已结束时不重复改状态。
    public SessionView stop(Long u,Long p,Long id) {
        guard.member(u,p); PlaytestSession s=owned(u,p,id);
        if("RUNNING".equals(s.getStatus())) { s.setStatus("ABORTED"); s.setFinishedAt(LocalDateTime.now()); sessions.updateById(s); }
        return view(s,node(s,s.getCurrentNodeId()),latest(id));
    }
    // 【解释】按项目和测试者过滤会话后分页，逐条装配展示状态。
    public Page<SessionView> list(Long u,Long p,int page,int size) {
        access.requireMember(u,p); paging(page,size);
        var q=new LambdaQueryWrapper<PlaytestSession>().eq(PlaytestSession::getProjectId,p).eq(PlaytestSession::getTesterId,u);
        long total=sessions.selectCount(q);
        // 【常见修改 3：按开始时间从新到旧】替换下一行为：
        // var rows=sessions.selectList(q.orderByDesc(PlaytestSession::getStartedAt)
        //         .orderByDesc(PlaytestSession::getId).last(limit(page,size)));
        // ID 用于时间相同时稳定排序；要最早的在前，把 StartedAt 对应的 Desc 改为 Asc。
        // 不要把时间排序接在原有 ID 排序后面，那样第一排序依据仍是 ID。
        // 保留上方 projectId/testerId 条件，不能为了列出更多记录而泄露其他用户的会话。
        var rows=sessions.selectList(q.orderByDesc(PlaytestSession::getId).last(limit(page,size)));
        return new Page<>(rows.stream().map(s->view(s,node(s,s.getCurrentNodeId()),latest(s.getId()))).toList(),page,size,total,(total+size-1)/size);
    }
    // 【解释】检查本人会话，分页返回每步变量、知识和跨局进度的前后快照。
    public Page<StepView> steps(Long u,Long p,Long id,int page,int size) {
        access.requireMember(u,p); owned(u,p,id); paging(page,size);
        var q=new LambdaQueryWrapper<PlaytestStep>().eq(PlaytestStep::getSessionId,id);
        long total=steps.selectCount(q);
        // 【常见修改 4：历史步骤最新在前】只把下一行 orderByAsc 改成 orderByDesc。
        // 影响的是历史列表展示；下面 latest() 原本就倒序找最新一步，不要一起反转它。
        // 前端若按时间线正序显示，要同步确认展示方式；验证翻页后的顺序也正确。
        var rows=steps.selectList(q.orderByAsc(PlaytestStep::getStepNo).last(limit(page,size)));
        return new Page<>(rows.stream().map(s->new StepView(s.getId(),s.getStepNo(),s.getNodeId(),s.getChoiceId(),
                decode(s.getStateBefore()),decode(s.getStateAfter()),s.getCreatedAt(),
                decodeKnowledge(s.getKnowledgeBefore()),decodeKnowledge(s.getKnowledgeAfter()),
                progress.decode(s.getProgressBefore()),progress.decode(s.getProgressAfter()))).toList(),page,size,total,(total+size-1)/size);
    }
    // 【解释】读取状态并结合当前进度计算可选/锁定选项；结束会话使用历史进度快照，不重新推进剧情。
    private SessionView view(PlaytestSession s,StoryNode current,PlaytestStep latest) {
        Map<String,String> state=decode(latest.getStateAfter());
        var currentProgress="RUNNING".equals(s.getStatus())?progress.snapshot(s.getTesterId(),s.getProjectId(),s.getReleaseId()):progress.decode(latest.getProgressAfter());
        List<ChoiceView> visible=new ArrayList<>();
        List<LockedChoiceView> locked=new ArrayList<>();
        if("RUNNING".equals(s.getStatus()) && !"ENDING".equals(current.getNodeType())) {
            var frozen=s.getReleaseId()==null?null:releases.snapshot(s.getProjectId(),s.getReleaseId());
            List<StateVariable> vars=frozen==null?catalog.variables(s.getProjectId()):frozen.variables();
            state=progress.overlay(vars,state,currentProgress);
            var candidates=frozen==null?choices.selectList(new LambdaQueryWrapper<StoryChoice>().eq(StoryChoice::getProjectId,s.getProjectId())
                    .eq(StoryChoice::getSourceNodeId,current.getId()).eq(StoryChoice::getEnabled,true)
                    .orderByAsc(StoryChoice::getSortOrder).orderByAsc(StoryChoice::getId))
                :frozen.choices().stream().filter(c->current.getId().equals(c.getSourceNodeId()) && Boolean.TRUE.equals(c.getEnabled())).toList();
            for(StoryChoice c:candidates) {
                var conditions=frozen==null?catalog.conditions(c.getId()):frozen.conditions().stream().filter(item->c.getId().equals(item.getChoiceId())).toList();
                boolean normal=engine.available(vars,conditions,state);
                boolean unlocked=unlock.available(unlock.decode(c.getUnlockRule()),vars,state,currentProgress);
                if(normal && unlocked)
                    visible.add(new ChoiceView(c.getId(),c.getTargetNodeId(),c.getChoiceText(),c.getSortOrder()));
                else locked.add(new LockedChoiceView(c.getId(),c.getChoiceText(),
                        (normal?"":"尚未满足基础变量条件；")+(unlocked?"":"解锁要求："+unlock.describe(unlock.decode(c.getUnlockRule())))));
            }
        }
        // 【常见修改 5：返回可选项数量】先给 DTO.SessionView 最后追加 int availableChoiceCount，
        // 再把下面构造调用末尾的 currentProgress,locked 改成 currentProgress,locked,visible.size()。
        // 无须修改 SQL/Entity；检查所有 new SessionView 调用并同步前端类型和测试。
        // deadEnd 表示“仍在运行但没有可选项”；正常完成结局不应被当作死路。
        return new SessionView(s.getId(),s.getProjectId(),s.getTesterId(),s.getStatus(),
                new NodeView(current.getId(),current.getNodeKey(),current.getTitle(),current.getContent(),current.getNodeType()),
                latest.getStepNo(),Collections.unmodifiableMap(state),visible,
                "RUNNING".equals(s.getStatus()) && visible.isEmpty(),s.getStartedAt(),s.getFinishedAt(),
                s.getReleaseId(),decodeKnowledge(latest.getKnowledgeAfter()),currentProgress,locked);
    }
    // 【解释】会话必须属于当前项目和用户，项目成员不能操作别人的会话。
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
    // 【解释】运行时只接受 NORMAL/ENDING，与编辑及导入约束保持一致。
    private void validateNodeType(StoryNode n) {
        // Both live and frozen nodes use the same runtime vocabulary.
        if(!Set.of("NORMAL","ENDING").contains(n.getNodeType()==null ? "" : n.getNodeType())) throw invalidState("节点类型不合法");
    }
    private StoryNode node(PlaytestSession session,Long id) {
        return session.getReleaseId()==null?node(session.getProjectId(),id):releases.snapshot(session.getProjectId(),session.getReleaseId()).node(id);
    }
    // 【解释】旧记录知识为 null 时兼容为空 Map，其他内容交给 JSON 解码。
    private Map<String,String> decodeKnowledge(String text) { return text==null?Map.of():decode(text); }
    // 【解释】步数倒序取第一条，缺失第 0 步等记录属于非法会话状态。
    private PlaytestStep latest(Long id) {
        PlaytestStep step=steps.selectOne(new LambdaQueryWrapper<PlaytestStep>().eq(PlaytestStep::getSessionId,id)
                .orderByDesc(PlaytestStep::getStepNo).last("LIMIT 1"));
        if(step==null) throw invalidState("会话缺少初始快照"); return step;
    }
    // 【解释】构造步骤对象并编码前后状态，真正插入在调用者完成。
    private PlaytestStep step(PlaytestSession s,int no,Long node,Long choice,Map<String,String> before,Map<String,String> after) {
        PlaytestStep step=new PlaytestStep(); step.setSessionId(s.getId()); step.setStepNo(no);
        step.setNodeId(node); step.setChoiceId(choice); step.setStateBefore(encode(before)); step.setStateAfter(encode(after));
        step.setCreatedAt(LocalDateTime.now()); return step;
    }
    // 【解释】变量/知识 Map 编码为 JSON，失败抛业务异常。
    private String encode(Map<String,String> state) {
        try { return json.writeValueAsString(state); }
        catch(JsonProcessingException e) { throw invalidState("状态快照无法序列化"); }
    }
    // 【解释】解析状态 JSON，并拒绝空对象引用或 null 值，避免损坏快照进入计算。
    private Map<String,String> decode(String text) {
        try {
            Map<String,String> result=json.readValue(text,new TypeReference<LinkedHashMap<String,String>>() {});
            if(result==null || result.values().stream().anyMatch(Objects::isNull)) throw invalidState("状态快照损坏");
            return result;
        } catch(JsonProcessingException | IllegalArgumentException e) { throw invalidState("状态快照损坏"); }
    }
    // 【解释】独立校验模拟模块的分页范围，与 FeatureScope.limit 不共用。
    private void paging(int page,int size) {
        // 【常见修改 6：每页最多 50 条】下面的 size>100 改为 size>50，提示改为“size范围为1至50”。
        // 此方法由会话列表和步骤列表共用，两类接口都会受影响；Controller 默认 size 是另一回事。
        // 验证 size=50 成功、51 拒绝、0 拒绝、page=0 拒绝；保留 limit() 中的 long 转换防止乘法溢出。
        if(page<1 || size<1 || size>100) throw new BusinessException("VALIDATION_ERROR","page至少为1，size范围为1至100",HttpStatus.BAD_REQUEST);
    }
    // 【解释】将已校验的整数转换为分页片段，偏移使用 long 计算。
    private String limit(int page,int size) { return "LIMIT "+size+" OFFSET "+((long)(page-1)*size); }
    // 【解释】模拟状态冲突统一用 INVALID_STATE/409。
    private BusinessException invalidState(String message) { return new BusinessException("INVALID_STATE",message,HttpStatus.CONFLICT); }
}
