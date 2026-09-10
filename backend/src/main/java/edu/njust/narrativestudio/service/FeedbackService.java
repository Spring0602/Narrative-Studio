package edu.njust.narrativestudio.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.njust.narrativestudio.dto.DatabaseDtos.*;
import edu.njust.narrativestudio.entity.*;
import edu.njust.narrativestudio.mapper.*;
import edu.njust.narrativestudio.exception.BusinessException;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class FeedbackService {
    private final TestFeedbackMapper feedback; private final PlaytestSessionMapper sessions; private final PlaytestStepMapper steps;
    private final ProjectAccessService access; private final ProjectMutationGuard guard;
    public FeedbackService(TestFeedbackMapper feedback,PlaytestSessionMapper sessions,PlaytestStepMapper steps,ProjectAccessService access,ProjectMutationGuard guard) {
        this.feedback=feedback;this.sessions=sessions;this.steps=steps;this.access=access;this.guard=guard;
    }
    public PlaytestService.Page<TestFeedback> list(Long u,Long p,int page,int size) {
        var member=access.requireMember(u,p);String limit=FeatureScope.limit(page,size);
        var q=new LambdaQueryWrapper<TestFeedback>().eq(TestFeedback::getProjectId,p);
        if("TESTER".equals(member.getMemberRole())) q.eq(TestFeedback::getReporterId,u);
        long total=feedback.selectCount(q);
        return new PlaytestService.Page<>(feedback.selectList(q.orderByDesc(TestFeedback::getId).last(limit)),page,size,total,(total+size-1)/size);
    }
    public TestFeedback get(Long u,Long p,Long id) {
        var member=access.requireMember(u,p);TestFeedback f=require(p,id);
        if("TESTER".equals(member.getMemberRole()) && !u.equals(f.getReporterId())) throw BusinessException.forbidden("只能读取自己的反馈");
        return f;
    }
    @Transactional
    public TestFeedback create(Long u,Long p,FeedbackRequest r) {
        guard.member(u,p);
        if(r.stepId()!=null && r.sessionId()==null) throw FeatureScope.invalid("指定步骤时必须同时指定会话");
        if(r.sessionId()!=null) {
            PlaytestSession s=sessions.selectById(r.sessionId());
            if(s==null || !p.equals(s.getProjectId()) || !u.equals(s.getTesterId())) throw BusinessException.notFound("自己的试玩会话不存在");
            if(r.stepId()!=null) {
                PlaytestStep step=steps.selectById(r.stepId());
                if(step==null || !s.getId().equals(step.getSessionId())) throw BusinessException.notFound("会话步骤不存在");
            }
        }
        TestFeedback f=new TestFeedback();f.setProjectId(p);f.setReporterId(u);f.setSessionId(r.sessionId());f.setStepId(r.stepId());
        f.setTitle(r.title().trim());f.setDescription(r.description());f.setStatus("OPEN");
        f.setCreatedAt(LocalDateTime.now());f.setUpdatedAt(f.getCreatedAt());feedback.insert(f);return f;
    }
    @Transactional
    public TestFeedback status(Long u,Long p,Long id,StatusRequest r) {
        guard.editor(u,p);TestFeedback f=require(p,id);f.setStatus(r.status());f.setUpdatedAt(LocalDateTime.now());feedback.updateById(f);return f;
    }
    private TestFeedback require(Long p,Long id) {
        TestFeedback f=feedback.selectById(id);if(f==null || !p.equals(f.getProjectId())) throw BusinessException.notFound("反馈不存在");return f;
    }
}
