package edu.njust.narrativestudio.controller;
import edu.njust.narrativestudio.common.ApiResponse;
import edu.njust.narrativestudio.config.CurrentUser;
import edu.njust.narrativestudio.dto.ProjectSaveDtos.*;
import edu.njust.narrativestudio.service.ProjectSaveService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/saves")
public class ProjectSaveController {
    // 【链路解释】state 查概览，detail 查完整快照，manual 新建手动记录，auto 更新本人草稿，restore 创建副本。
    // Controller 只接收与包装；保留数量/修订号/恢复策略在 ProjectSaveService，不在路由里判断。
    // 【修改提示】默认备注/名称长度看 Service/DTO；路由或字段名变化要同步前端，恢复不能只改路由名就变成覆盖原项目。
    private final ProjectSaveService service;private final CurrentUser current;
    public ProjectSaveController(ProjectSaveService service,CurrentUser current){this.service=service;this.current=current;}
    @GetMapping public ApiResponse<State> state(Authentication a,@PathVariable Long projectId){return ApiResponse.ok(service.state(current.id(a),projectId));}
    @GetMapping("/{id}") public ApiResponse<Detail> detail(Authentication a,@PathVariable Long projectId,@PathVariable Long id){return ApiResponse.ok(service.detail(current.id(a),projectId,id));}
    @PostMapping public ApiResponse<SaveResult> manual(Authentication a,@PathVariable Long projectId,@Valid @RequestBody SaveRequest r){return ApiResponse.ok(service.manual(current.id(a),projectId,r));}
    @PutMapping("/auto") public ApiResponse<SaveResult> auto(Authentication a,@PathVariable Long projectId,@Valid @RequestBody SaveRequest r){return ApiResponse.ok(service.automatic(current.id(a),projectId,r));}
    @PostMapping("/{id}/restore-copy") public ApiResponse<Restored> restore(Authentication a,@PathVariable Long projectId,@PathVariable Long id,@Valid @RequestBody RestoreRequest r){return ApiResponse.ok(service.restore(current.id(a),projectId,id,r));}
}
