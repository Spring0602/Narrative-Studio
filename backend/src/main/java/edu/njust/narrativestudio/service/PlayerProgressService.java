package edu.njust.narrativestudio.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.njust.narrativestudio.dto.ProgressSnapshot;
import edu.njust.narrativestudio.engine.RuleEngine;
import edu.njust.narrativestudio.entity.*;
import edu.njust.narrativestudio.mapper.*;
import edu.njust.narrativestudio.exception.BusinessException;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class PlayerProgressService {
    public record View(Long releaseId,long revision,ProgressSnapshot progress) {}
    public record Reset(@Min(0) long expectedRevision,@NotNull @AssertTrue Boolean confirm) {}
    public record Override(@Min(0) long expectedRevision,@NotNull @AssertTrue Boolean confirm,
            @NotNull @Size(max=200) Map<String,String> values,
            @NotNull @Size(max=500) Set<String> completedEndings) {}
    private final PlayerProgressMapper mapper;private final PlaytestSessionMapper sessions;
    private final ProjectAccessService access;private final ProjectMutationGuard guard;
    private final ReleaseService releases;private final RuleCatalog catalog;private final StoryNodeMapper nodes;
    private final ObjectMapper json;private final RuleEngine engine;
    public PlayerProgressService(PlayerProgressMapper mapper,PlaytestSessionMapper sessions,ProjectAccessService access,
            ProjectMutationGuard guard,ReleaseService releases,RuleCatalog catalog,StoryNodeMapper nodes,ObjectMapper json,RuleEngine engine) {
        this.mapper=mapper;this.sessions=sessions;this.access=access;this.guard=guard;this.releases=releases;
        this.catalog=catalog;this.nodes=nodes;this.json=json;this.engine=engine;
    }
    // 【解释】按用户、项目、版本隔离进度；编辑稿用 versionKey=0，发布稿使用版本 ID。
    private PlayerProgress row(Long user,Long project,Long release) {
        return mapper.selectOne(new LambdaQueryWrapper<PlayerProgress>().eq(PlayerProgress::getProjectId,project)
                .eq(PlayerProgress::getTesterId,user).eq(PlayerProgress::getVersionKey,release==null?0L:release));
    }
    // 【解释】内部读取进度，无记录时从已有完成会话引导初始化；由外层业务保证权限。
    public ProgressSnapshot snapshot(Long user,Long project,Long release) {
        var row=row(user,project,release);return row==null?bootstrap(user,project,release):decode(row.getProgressJson());
    }
    // 【解释】对外读取，先检查成员和版本归属，再返回修订号与进度。
    public View get(Long user,Long project,Long release) {
        access.requireMember(user,project);if(release!=null) releases.get(user,project,release);
        var row=row(user,project,release);
        return new View(release,row==null?0:row.getRevision(),row==null?bootstrap(user,project,release):decode(row.getProgressJson()));
    }
    // Existing COMPLETED sessions can seed ending prerequisites once; an explicit empty reset row prevents reimport.
    // 【解释】从此用户此版本已完成的会话提取结局标识；显式重置后的空记录会阻止再次导入这些旧结局。
    private ProgressSnapshot bootstrap(Long user,Long project,Long release) {
        var completed=new HashSet<>(sessions.completedNodes(user,project,release));
        if(completed.isEmpty()) return ProgressSnapshot.empty();
        var ns=release==null?nodes.selectList(new LambdaQueryWrapper<StoryNode>().eq(StoryNode::getProjectId,project)):releases.snapshot(project,release).nodes();
        Set<String> endings=new LinkedHashSet<>();
        ns.stream().filter(n->completed.contains(n.getId()) && "ENDING".equals(n.getNodeType())).forEach(n->endings.add(n.getNodeKey()));
        return new ProgressSnapshot(Map.of(),endings,endings);
    }
    // 【解释】复制当前单局状态，仅用跨局进度覆盖 PROFILE 变量，没有值则用定义初始值，不修改传入 Map。
    public Map<String,String> overlay(List<StateVariable> vars,Map<String,String> session,ProgressSnapshot progress) {
        var result=new LinkedHashMap<>(session);
        for(var v:vars) if("PROFILE".equals(v.getPersistenceScope()))
            result.put(v.getVariableKey(),engine.validateValue(v.getValueType(),progress.values().getOrDefault(v.getVariableKey(),v.getInitialValue())));
        return result;
    }
    // Caller holds project lock and transaction. Successful steps persist PROFILE changes immediately.
    // 【解释】到达节点时保存 PROFILE 变量、访问节点集合和结局集合；调用者必须持有项目锁与写事务。
    public ProgressSnapshot enter(Long user,Long project,Long release,List<StateVariable> vars,
            Map<String,String> state,StoryNode node,ProgressSnapshot before) {
        Map<String,String> values=new LinkedHashMap<>();
        for(var v:vars) if("PROFILE".equals(v.getPersistenceScope()))
            values.put(v.getVariableKey(),engine.validateValue(v.getValueType(),state.get(v.getVariableKey())));
        var endings=new LinkedHashSet<>(before.completedEndings());var visited=new LinkedHashSet<>(before.visitedNodes());
        visited.add(node.getNodeKey());if("ENDING".equals(node.getNodeType())) endings.add(node.getNodeKey());
        var after=new ProgressSnapshot(values,endings,visited);save(user,project,release,after);return after;
    }
    // 【解释】新进度修订号为 1，已有记录则递增修订号并写 JSON。
    private void save(Long user,Long project,Long release,ProgressSnapshot value) {
        var row=row(user,project,release);
        if(row==null) {
            row=new PlayerProgress();row.setProjectId(project);row.setTesterId(user);row.setVersionKey(release==null?0L:release);
            row.setRevision(1L);row.setProgressJson(encode(value));mapper.insert(row);
        } else {
            row.setRevision(Math.incrementExact(row.getRevision()));row.setProgressJson(encode(value));mapper.updateById(row);
        }
    }
    @Transactional
    // 【解释】成员明确确认、无本人运行会话且修订号匹配后，写空进度记录，而不是物理删除。
    public View reset(Long user,Long project,Long release,Reset request) {
        guard.member(user,project);check(user,project,release,request.expectedRevision(),request.confirm());
        save(user,project,release,ProgressSnapshot.empty());return get(user,project,release);
    }
    @Transactional
    // 【解释】编辑者设置本人的测试存档，只接受本版本 PROFILE 变量和真实结局。
    // 【修改提示】这是整份测试进度替换，不是局部追加；不能为便于测试而允许客户端改其他用户进度。
    public View override(Long user,Long project,Long release,Override request) {
        guard.editor(user,project);check(user,project,release,request.expectedRevision(),request.confirm());
        var frozen=release==null?null:releases.get(user,project,release);
        var vars=frozen==null?catalog.variables(project):frozen.variables();
        var ns=frozen==null?nodes.selectList(new LambdaQueryWrapper<StoryNode>().eq(StoryNode::getProjectId,project)):frozen.nodes();
        Map<String,String> values=new LinkedHashMap<>();
        for(var entry:request.values().entrySet()) {
            var variable=vars.stream().filter(v->"PROFILE".equals(v.getPersistenceScope()) && v.getVariableKey().equals(entry.getKey()))
                    .findFirst().orElseThrow(()->FeatureScope.invalid("只能设置本版本的跨局变量"));
            values.put(entry.getKey(),engine.validateValue(variable.getValueType(),entry.getValue()));
        }
        for(var key:request.completedEndings()) if(key==null || ns.stream().noneMatch(n->key.equals(n.getNodeKey()) && "ENDING".equals(n.getNodeType())))
            throw FeatureScope.invalid("测试存档引用了不存在的结局");
        save(user,project,release,new ProgressSnapshot(values,request.completedEndings(),request.completedEndings()));
        return get(user,project,release);
    }
    // 【解释】统一检查确认、版本、本人运行会话和修订号。
    // 【常见修改】可细化冲突提示文字；不可删 revision 检查来解决多窗口冲突。
    private void check(Long user,Long project,Long release,long revision,Boolean confirm) {
        if(!Boolean.TRUE.equals(confirm)) throw FeatureScope.invalid("请确认修改本人测试存档");
        if(release!=null) releases.get(user,project,release);
        var q=new LambdaQueryWrapper<PlaytestSession>().eq(PlaytestSession::getProjectId,project).eq(PlaytestSession::getTesterId,user)
                .eq(PlaytestSession::getStatus,"RUNNING");
        if(release==null) q.isNull(PlaytestSession::getReleaseId);else q.eq(PlaytestSession::getReleaseId,release);
        if(sessions.selectCount(q)>0) throw BusinessException.conflict("请先终止此版本下本人所有运行中的模拟，再修改跨局进度");
        var row=row(user,project,release);
        if(revision!=(row==null?0:row.getRevision())) throw BusinessException.conflict("跨局进度已变化，请刷新后重试");
    }
    // 【解释】JSON 转进度对象，null 返回空进度，损坏则报错。
    public ProgressSnapshot decode(String text) {
        if(text==null) return ProgressSnapshot.empty();
        try {return json.readValue(text,ProgressSnapshot.class);}
        catch(Exception ex) {throw BusinessException.conflict("跨局进度快照损坏");}
    }
    // 【解释】进度对象转 JSON，失败时不能假装保存成功。
    public String encode(ProgressSnapshot value) {
        try {return json.writeValueAsString(value);}
        catch(Exception ex) {throw BusinessException.conflict("无法编码跨局进度");}
    }
}
