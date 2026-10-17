package edu.njust.narrativestudio.controller;
import edu.njust.narrativestudio.common.ApiResponse;
import edu.njust.narrativestudio.config.CurrentUser;
import edu.njust.narrativestudio.service.PlayerProgressService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/projects/{projectId}/player-progress")
// 【MVC 入口】get 查看本人进度；reset 清空；override 设置测试存档。releaseId 可选，请求类型定义在 PlayerProgressService 内。
// 【修改提示】路由、参数名变化需同步前端 API 和测试；业务权限/关联校验在 Service 保留，不在这里绕过。
public class PlayerProgressController {
    private final PlayerProgressService service;private final CurrentUser current;
    public PlayerProgressController(PlayerProgressService service,CurrentUser current) {this.service=service;this.current=current;}
    @GetMapping public ApiResponse<PlayerProgressService.View> get(Authentication auth,@PathVariable Long projectId,@RequestParam(required=false) Long releaseId) {
        return ApiResponse.ok(service.get(current.id(auth),projectId,releaseId));
    }
    @PostMapping("/reset") public ApiResponse<PlayerProgressService.View> reset(Authentication auth,@PathVariable Long projectId,
            @RequestParam(required=false) Long releaseId,@Valid @RequestBody PlayerProgressService.Reset request) {
        return ApiResponse.ok(service.reset(current.id(auth),projectId,releaseId,request));
    }
    @PutMapping public ApiResponse<PlayerProgressService.View> override(Authentication auth,@PathVariable Long projectId,
            @RequestParam(required=false) Long releaseId,@Valid @RequestBody PlayerProgressService.Override request) {
        return ApiResponse.ok(service.override(current.id(auth),projectId,releaseId,request));
    }
}
