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
@RequestMapping("/api/projects/{projectId}/issues")
public class IssueController {
    private final IssueService service;private final CurrentUser user;
    public IssueController(IssueService service,CurrentUser user) {this.service=service;this.user=user;}
    @GetMapping
    public ApiResponse<PlaytestService.Page<DetectedIssue>> list(Authentication auth,@PathVariable Long projectId,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.ok(service.list(user.id(auth),projectId,page,size));
    }
    @GetMapping("/{id}")
    public ApiResponse<DetectedIssue> get(Authentication auth,@PathVariable Long projectId,@PathVariable Long id) {
        return ApiResponse.ok(service.get(user.id(auth),projectId,id));
    }
    @PutMapping("/{id}/status")
    public ApiResponse<DetectedIssue> status(Authentication auth,@PathVariable Long projectId,@PathVariable Long id,@Valid @RequestBody StatusRequest r) {
        return ApiResponse.ok(service.status(user.id(auth),projectId,id,r));
    }
}
