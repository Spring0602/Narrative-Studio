package edu.njust.narrativestudio.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import edu.njust.narrativestudio.dto.DatabaseDtos.*;
import edu.njust.narrativestudio.dto.StoryGraphDtos.*;
import edu.njust.narrativestudio.entity.StoryChoiceDraft;
import edu.njust.narrativestudio.mapper.StoryChoiceDraftMapper;
import edu.njust.narrativestudio.exception.BusinessException;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class ChoiceDraftService {
    private final StoryChoiceDraftMapper drafts; private final ProjectAccessService access;
    private final ProjectMutationGuard guard; private final FeatureScope scope; private final StoryGraphService graph;
    public ChoiceDraftService(StoryChoiceDraftMapper drafts,ProjectAccessService access,ProjectMutationGuard guard,FeatureScope scope,StoryGraphService graph) {
        this.drafts=drafts;this.access=access;this.guard=guard;this.scope=scope;this.graph=graph;
    }
    // 【解释】分页返回尚未转正的选项草稿，按排序值和 ID 升序。
    public PlaytestService.Page<StoryChoiceDraft> list(Long u,Long p,int page,int size) {
        access.requireMember(u,p);String limit=FeatureScope.limit(page,size);
        var q=new LambdaQueryWrapper<StoryChoiceDraft>().eq(StoryChoiceDraft::getProjectId,p);
        long total=drafts.selectCount(q);
        return new PlaytestService.Page<>(drafts.selectList(q.orderByAsc(StoryChoiceDraft::getSortOrder).orderByAsc(StoryChoiceDraft::getId).last(limit)),page,size,total,(total+size-1)/size);
    }
    // 【解释】查本项目的单个草稿。
    public StoryChoiceDraft get(Long u,Long p,Long id) { access.requireMember(u,p);return require(p,id); }
    @Transactional
    // 【解释】无 id 新增，有 id 修改；源节点不能是结局，草稿允许暂未填写正式选项所需的内容。
    // 【常见修改】文本长度在 DatabaseDtos.DraftRequest，改动还要核对转正使用的 ChoiceRequest 限制。
    public StoryChoiceDraft save(Long u,Long p,Long id,DraftRequest r) {
        guard.editor(u,p);
        if("ENDING".equals(scope.node(p,r.sourceNodeId()).getNodeType())) throw BusinessException.conflict("结局节点不能创建选项草稿");
        StoryChoiceDraft d=id==null?new StoryChoiceDraft():require(p,id);
        d.setProjectId(p);d.setSourceNodeId(r.sourceNodeId());d.setChoiceText(r.choiceText());d.setSortOrder(r.sortOrder());
        d.setUpdatedAt(LocalDateTime.now());
        if(id==null) { d.setCreatedBy(u);d.setCreatedAt(d.getUpdatedAt());drafts.insert(d); }
        else drafts.update(null,new LambdaUpdateWrapper<StoryChoiceDraft>().eq(StoryChoiceDraft::getId,id)
            .set(StoryChoiceDraft::getSourceNodeId,d.getSourceNodeId()).set(StoryChoiceDraft::getChoiceText,d.getChoiceText())
            .set(StoryChoiceDraft::getSortOrder,d.getSortOrder()).set(StoryChoiceDraft::getUpdatedAt,d.getUpdatedAt()));
        return d;
    }
    @Transactional
    // 【解释】编辑者校验草稿归属后删除。
    public void delete(Long u,Long p,Long id) { guard.editor(u,p);require(p,id);drafts.deleteById(id); }
    @Transactional
    // 【解释】补上目标节点，用图服务创建正式选项，成功后删草稿；同一事务避免转正失败却丢失草稿。
    // 【修改提示】转正提示文字可改，但不能省略非空文本、目标归属和结局约束。
    public ChoiceSummary promote(Long u,Long p,Long id,PromoteRequest r) {
        guard.editor(u,p);StoryChoiceDraft d=require(p,id);
        if(d.getChoiceText()==null || d.getChoiceText().isBlank()) throw FeatureScope.invalid("转正前须填写选项文本");
        ChoiceSummary result=graph.createChoice(u,p,d.getSourceNodeId(),new ChoiceRequest(r.targetNodeId(),d.getChoiceText(),d.getSortOrder(),true));
        drafts.deleteById(id);return result;
    }
    // 【解释】草稿 ID 与项目匹配。
    private StoryChoiceDraft require(Long p,Long id) {
        StoryChoiceDraft d=drafts.selectById(id);
        if(d==null || !p.equals(d.getProjectId())) throw BusinessException.notFound("选项草稿不存在");return d;
    }
}
