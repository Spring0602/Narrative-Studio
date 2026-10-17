package edu.njust.narrativestudio.controller;

import edu.njust.narrativestudio.common.ApiResponse;
import edu.njust.narrativestudio.config.CurrentUser;
import edu.njust.narrativestudio.dto.ProjectDtos;
import edu.njust.narrativestudio.service.ProjectService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
// 【MVC 入口】create 建项目；list 列我的项目；get 查详情；update 改基本信息；archive 虽用 DELETE 路由，实际是归档而非物理删除。
// 【修改提示】路由、参数名变化需同步前端 API 和测试；业务权限/关联校验在 Service 保留，不在这里绕过。
public class ProjectController {
    private final ProjectService projectService;
    private final CurrentUser currentUser;

    public ProjectController(ProjectService projectService, CurrentUser currentUser) {
        this.projectService = projectService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ApiResponse<ProjectDtos.Summary> create(Authentication auth,
                                                    @Valid @RequestBody ProjectDtos.CreateRequest request) {
        return ApiResponse.ok(projectService.create(currentUser.id(auth), request));
    }

    @GetMapping
    public ApiResponse<List<ProjectDtos.Summary>> list(Authentication auth) {
        return ApiResponse.ok(projectService.listAccessible(currentUser.id(auth)));
    }

    @GetMapping("/{projectId}")
    public ApiResponse<ProjectDtos.Summary> get(Authentication auth, @PathVariable Long projectId) {
        return ApiResponse.ok(projectService.getAccessible(currentUser.id(auth), projectId));
    }

    @PutMapping("/{projectId}")
    public ApiResponse<ProjectDtos.Summary> update(Authentication auth, @PathVariable Long projectId,
                                                    @Valid @RequestBody ProjectDtos.UpdateRequest request) {
        return ApiResponse.ok(projectService.update(currentUser.id(auth), projectId, request));
    }

    @DeleteMapping("/{projectId}")
    public ApiResponse<Void> archive(Authentication auth, @PathVariable Long projectId) {
        projectService.archive(currentUser.id(auth), projectId);
        return ApiResponse.ok(null);
    }
}
