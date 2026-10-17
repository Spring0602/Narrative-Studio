package edu.njust.narrativestudio.controller;
import edu.njust.narrativestudio.common.ApiResponse;
import edu.njust.narrativestudio.config.CurrentUser;
import edu.njust.narrativestudio.dto.PlaytestDtos.*;
import edu.njust.narrativestudio.service.PlaytestService;
import edu.njust.narrativestudio.service.PlaytestService.Page;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
// 答辩修改导航：普通接口改动先看这里；计算规则在 ServiceImpl/engine，不在 Controller 写。
// 修改公共路径时，要同步前端请求和接口测试。登录用户 ID 始终来自认证信息，不能信任请求传入的 userId。
@RequestMapping("/api/projects/{projectId}/playtests")
public class PlaytestController {
    private final PlaytestService service;
    private final CurrentUser user;
    public PlaytestController(PlaytestService service,CurrentUser user) { this.service=service; this.user=user; }
    @PostMapping
    public ApiResponse<SessionView> start(Authentication auth,@PathVariable Long projectId) {
        return ApiResponse.ok(service.start(user.id(auth),projectId));
    }
    // 【常见修改：默认每页 10 条】把本方法参数中的 defaultValue="20" 改成 defaultValue="10"。
    // 只在没有传 size 时采用默认值；前端若明确传 size=20，还要同步前端。
    // 下方 steps 接口有独立默认值；若要求统一分页，两处一起改。最大值在实现类 paging() 中。
    @GetMapping
    public ApiResponse<Page<SessionView>> list(Authentication auth,@PathVariable Long projectId,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.ok(service.list(user.id(auth),projectId,page,size));
    }
    @GetMapping("/{sessionId}")
    public ApiResponse<SessionView> get(Authentication auth,@PathVariable Long projectId,@PathVariable Long sessionId) {
        return ApiResponse.ok(service.get(user.id(auth),projectId,sessionId));
    }
    // 【修改请求校验提示】到 DTO 的 AdvanceRequest 修改注解 message，保留此处 @Valid。
    // expectedStepNo 是前端当前步数，不是目标步数；不要在这里 +1，也不要改成服务器最新步数。
    // 否则过期页面/重复点击可能绕过实现类的步数检查。
    @PostMapping("/{sessionId}/choices/{choiceId}")
    public ApiResponse<SessionView> advance(Authentication auth,@PathVariable Long projectId,@PathVariable Long sessionId,
            @PathVariable Long choiceId,@Valid @RequestBody AdvanceRequest request) {
        return ApiResponse.ok(service.advance(user.id(auth),projectId,sessionId,choiceId,request.expectedStepNo()));
    }
    @PostMapping("/{sessionId}/restart")
    public ApiResponse<SessionView> restart(Authentication auth,@PathVariable Long projectId,@PathVariable Long sessionId) {
        return ApiResponse.ok(service.restart(user.id(auth),projectId,sessionId));
    }
    @PostMapping("/{sessionId}/stop")
    public ApiResponse<SessionView> stop(Authentication auth,@PathVariable Long projectId,@PathVariable Long sessionId) {
        return ApiResponse.ok(service.stop(user.id(auth),projectId,sessionId));
    }
    // 【默认分页】此处的 size 与上方会话列表分别配置；历史正序/倒序则改实现类 steps() 的查询。
    @GetMapping("/{sessionId}/steps")
    public ApiResponse<Page<StepView>> steps(Authentication auth,@PathVariable Long projectId,@PathVariable Long sessionId,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.ok(service.steps(user.id(auth),projectId,sessionId,page,size));
    }
}
