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
@RequestMapping("/api/projects/{projectId}/choice-drafts")
// 【MVC 入口】list/get 查草稿；create/update 以 null/已有 id 复用 save；delete 删除；promote 补目标转正式选项，路由 publish 不等于发布整个项目。
// 【修改提示】路由、参数名变化需同步前端 API 和测试；业务权限/关联校验在 Service 保留，不在这里绕过。
// 【常见修改】默认每页 10：此类 size 的 defaultValue="20" 改为 "10"；前端显式传 size 时还需改前端，上限由 Service/FeatureScope 控制。
public class ChoiceDraftController {
    private final ChoiceDraftService service;private final CurrentUser user;
    public ChoiceDraftController(ChoiceDraftService service,CurrentUser user) {this.service=service;this.user=user;}
    @GetMapping
    public ApiResponse<PlaytestService.Page<StoryChoiceDraft>> list(Authentication auth,@PathVariable Long projectId,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.ok(service.list(user.id(auth),projectId,page,size));
    }
    @GetMapping("/{id}")
    public ApiResponse<StoryChoiceDraft> get(Authentication auth,@PathVariable Long projectId,@PathVariable Long id) {
        return ApiResponse.ok(service.get(user.id(auth),projectId,id));
    }
    @PostMapping
    public ApiResponse<StoryChoiceDraft> create(Authentication auth,@PathVariable Long projectId,@Valid @RequestBody DraftRequest r) {
        return ApiResponse.ok(service.save(user.id(auth),projectId,null,r));
    }
    @PutMapping("/{id}")
    public ApiResponse<StoryChoiceDraft> update(Authentication auth,@PathVariable Long projectId,@PathVariable Long id,@Valid @RequestBody DraftRequest r) {
        return ApiResponse.ok(service.save(user.id(auth),projectId,id,r));
    }
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(Authentication auth,@PathVariable Long projectId,@PathVariable Long id) {
        service.delete(user.id(auth),projectId,id);return ApiResponse.ok(null);
    }
    @PostMapping("/{id}/publish")
    public ApiResponse<edu.njust.narrativestudio.dto.StoryGraphDtos.ChoiceSummary> promote(Authentication auth,@PathVariable Long projectId,@PathVariable Long id,@Valid @RequestBody PromoteRequest r) {
        return ApiResponse.ok(service.promote(user.id(auth),projectId,id,r));
    }
}
