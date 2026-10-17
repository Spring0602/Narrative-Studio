package edu.njust.narrativestudio.controller;

import edu.njust.narrativestudio.common.ApiResponse;
import edu.njust.narrativestudio.config.CurrentUser;
import edu.njust.narrativestudio.service.AiDialogueService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/ai/dialogue-candidates")
// 【MVC 入口】generate 生成未保存候选，要求明确同意发送选中资料。未配置 AI 时明确不可用，不影响核心业务。
// 【修改提示】路由、参数名变化需同步前端 API 和测试；业务权限/关联校验在 Service 保留，不在这里绕过。
public class AiDialogueController {
    private final AiDialogueService service;
    private final CurrentUser currentUser;
    public AiDialogueController(AiDialogueService service,CurrentUser currentUser) {
        this.service=service;this.currentUser=currentUser;
    }
    @PostMapping
    public ApiResponse<AiDialogueService.Candidate> generate(Authentication auth,@PathVariable Long projectId,
            @Valid @RequestBody AiDialogueService.Request request) {
        return ApiResponse.ok(service.generate(currentUser.id(auth),projectId,request));
    }
}
