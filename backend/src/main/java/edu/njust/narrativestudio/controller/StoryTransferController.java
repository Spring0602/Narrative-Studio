package edu.njust.narrativestudio.controller;

import edu.njust.narrativestudio.common.ApiResponse;
import edu.njust.narrativestudio.config.CurrentUser;
import edu.njust.narrativestudio.dto.*;
import edu.njust.narrativestudio.service.StoryTransferService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects")
// 【MVC 入口】exportStory 导出统一剧情 JSON 文档；importStory 创建新项目，不覆盖现有项目；与 Excel 共用底层导入逻辑。
// 【修改提示】路由、参数名变化需同步前端 API 和测试；业务权限/关联校验在 Service 保留，不在这里绕过。
public class StoryTransferController {
    private final StoryTransferService service;
    private final CurrentUser currentUser;
    public StoryTransferController(StoryTransferService service,CurrentUser currentUser) {
        this.service=service;this.currentUser=currentUser;
    }
    @GetMapping("/{projectId}/export")
    public ApiResponse<StoryTransferDtos.Document> exportStory(Authentication auth,@PathVariable Long projectId) {
        return ApiResponse.ok(service.exportStory(currentUser.id(auth),projectId));
    }
    @PostMapping("/import")
    public ApiResponse<ProjectDtos.Summary> importStory(Authentication auth,
            @Valid @RequestBody StoryTransferDtos.Document document) {
        return ApiResponse.ok(service.importStory(currentUser.id(auth),document));
    }
}
