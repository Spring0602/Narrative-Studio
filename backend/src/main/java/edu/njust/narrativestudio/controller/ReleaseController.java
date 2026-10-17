package edu.njust.narrativestudio.controller;
import edu.njust.narrativestudio.common.ApiResponse;
import edu.njust.narrativestudio.config.CurrentUser;
import edu.njust.narrativestudio.dto.ReleaseSnapshot;
import edu.njust.narrativestudio.dto.PlaytestDtos.SessionView;
import edu.njust.narrativestudio.entity.DetectedIssue;
import edu.njust.narrativestudio.service.*;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/projects/{projectId}")
// 【MVC 入口】list/get 查发布版本；publish 冻结版本；start 启动指定版本模拟；analyze 调 IssueService 生成结构检测问题。
// 【修改提示】路由、参数名变化需同步前端 API 和测试；业务权限/关联校验在 Service 保留，不在这里绕过。
// 【常见修改】默认每页 10：此类 size 的 defaultValue="20" 改为 "10"；前端显式传 size 时还需改前端，上限由 Service/FeatureScope 控制。
public class ReleaseController {
    private final ReleaseService releases;private final PlaytestService playtests;private final IssueService issues;private final CurrentUser user;
    public ReleaseController(ReleaseService releases,PlaytestService playtests,IssueService issues,CurrentUser user) {
        this.releases=releases;this.playtests=playtests;this.issues=issues;this.user=user;
    }
    @GetMapping("/releases")
    public ApiResponse<PlaytestService.Page<ReleaseService.Summary>> list(Authentication auth,@PathVariable Long projectId,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.ok(releases.list(user.id(auth),projectId,page,size));
    }
    @PostMapping("/releases")
    public ApiResponse<ReleaseService.Summary> publish(Authentication auth,@PathVariable Long projectId) {
        return ApiResponse.ok(releases.publish(user.id(auth),projectId));
    }
    @GetMapping("/releases/{releaseId}")
    public ApiResponse<ReleaseSnapshot> get(Authentication auth,@PathVariable Long projectId,@PathVariable Long releaseId) {
        return ApiResponse.ok(releases.get(user.id(auth),projectId,releaseId));
    }
    @PostMapping("/releases/{releaseId}/playtests")
    public ApiResponse<SessionView> start(Authentication auth,@PathVariable Long projectId,@PathVariable Long releaseId) {
        return ApiResponse.ok(playtests.startRelease(user.id(auth),projectId,releaseId));
    }
    @PostMapping("/analysis-runs")
    public ApiResponse<List<DetectedIssue>> analyze(Authentication auth,@PathVariable Long projectId) {
        return ApiResponse.ok(issues.analyze(user.id(auth),projectId));
    }
}
