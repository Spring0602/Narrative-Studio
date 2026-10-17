package edu.njust.narrativestudio.controller;
import edu.njust.narrativestudio.common.ApiResponse;
import edu.njust.narrativestudio.config.CurrentUser;
import edu.njust.narrativestudio.dto.DatabaseDtos.*;
import edu.njust.narrativestudio.entity.*;
import edu.njust.narrativestudio.service.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/projects/{projectId}/feedback")
// 【MVC 入口】list/get 查反馈；create 提交反馈并可关联自己的会话/步骤；status 由编辑者处理。
// 【修改提示】路由、参数名变化需同步前端 API 和测试；业务权限/关联校验在 Service 保留，不在这里绕过。
// 【常见修改】默认每页 10：此类 size 的 defaultValue="20" 改为 "10"；前端显式传 size 时还需改前端，上限由 Service/FeatureScope 控制。
public class FeedbackController {
    private final FeedbackService service;private final CurrentUser user;
    public FeedbackController(FeedbackService service,CurrentUser user) {this.service=service;this.user=user;}
    @GetMapping
    public ApiResponse<PlaytestService.Page<TestFeedback>> list(Authentication auth,@PathVariable Long projectId,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.ok(service.list(user.id(auth),projectId,page,size));
    }
    @GetMapping("/{id}")
    public ApiResponse<TestFeedback> get(Authentication auth,@PathVariable Long projectId,@PathVariable Long id) {
        return ApiResponse.ok(service.get(user.id(auth),projectId,id));
    }
    @PostMapping
    public ApiResponse<TestFeedback> create(Authentication auth,@PathVariable Long projectId,@Valid @RequestBody FeedbackRequest r) {
        return ApiResponse.ok(service.create(user.id(auth),projectId,r));
    }
    @PutMapping("/{id}/status")
    public ApiResponse<TestFeedback> status(Authentication auth,@PathVariable Long projectId,@PathVariable Long id,@Valid @RequestBody StatusRequest r) {
        return ApiResponse.ok(service.status(user.id(auth),projectId,id,r));
    }
}
