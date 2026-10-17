package edu.njust.narrativestudio.controller;

import edu.njust.narrativestudio.common.ApiResponse;
import edu.njust.narrativestudio.config.CurrentUser;
import edu.njust.narrativestudio.service.EndingCoverageService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/ending-coverage")
// 【MVC 入口】get 统计本人指定版本结局覆盖率；releaseId 未传表示编辑稿，不能混合多个版本的结局定义。
// 【修改提示】路由、参数名变化需同步前端 API 和测试；业务权限/关联校验在 Service 保留，不在这里绕过。
public class EndingCoverageController {
    private final EndingCoverageService service;
    private final CurrentUser currentUser;
    public EndingCoverageController(EndingCoverageService service,CurrentUser currentUser) {
        this.service=service;this.currentUser=currentUser;
    }
    @GetMapping
    public ApiResponse<EndingCoverageService.Coverage> get(Authentication auth,@PathVariable Long projectId,
            @RequestParam(required=false) Long releaseId) {
        return ApiResponse.ok(service.get(currentUser.id(auth),projectId,releaseId));
    }
}
