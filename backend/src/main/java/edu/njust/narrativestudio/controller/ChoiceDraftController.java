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
