package edu.njust.narrativestudio.controller;
import edu.njust.narrativestudio.common.ApiResponse;
import edu.njust.narrativestudio.config.CurrentUser;
import edu.njust.narrativestudio.dto.PlaytestDtos.*;
import edu.njust.narrativestudio.service.PlaytestService;
import edu.njust.narrativestudio.service.PlaytestService.Page;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/playtests")
public class PlaytestController {
    private final PlaytestService service;
    private final CurrentUser user;
    public PlaytestController(PlaytestService service,CurrentUser user) { this.service=service; this.user=user; }
    @PostMapping
    public ApiResponse<SessionView> start(Authentication auth,@PathVariable Long projectId) {
        return ApiResponse.ok(service.start(user.id(auth),projectId));
    }
    @GetMapping
    public ApiResponse<Page<SessionView>> list(Authentication auth,@PathVariable Long projectId,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.ok(service.list(user.id(auth),projectId,page,size));
    }
    @GetMapping("/{sessionId}")
    public ApiResponse<SessionView> get(Authentication auth,@PathVariable Long projectId,@PathVariable Long sessionId) {
        return ApiResponse.ok(service.get(user.id(auth),projectId,sessionId));
    }
    @PostMapping("/{sessionId}/choices/{choiceId}")
    public ApiResponse<SessionView> advance(Authentication auth,@PathVariable Long projectId,@PathVariable Long sessionId,
            @PathVariable Long choiceId,@Valid @RequestBody AdvanceRequest request) {
        return ApiResponse.ok(service.advance(user.id(auth),projectId,sessionId,choiceId,request.expectedStepNo()));
    }
    @PostMapping("/{sessionId}/restart")
    public ApiResponse<SessionView> restart(Authentication auth,@PathVariable Long projectId,@PathVariable Long sessionId) {
        return ApiResponse.ok(service.restart(user.id(auth),projectId,sessionId));
    }
    @PostMapping("/{sessionId}/stop")
    public ApiResponse<SessionView> stop(Authentication auth,@PathVariable Long projectId,@PathVariable Long sessionId) {
        return ApiResponse.ok(service.stop(user.id(auth),projectId,sessionId));
    }
    @GetMapping("/{sessionId}/steps")
    public ApiResponse<Page<StepView>> steps(Authentication auth,@PathVariable Long projectId,@PathVariable Long sessionId,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.ok(service.steps(user.id(auth),projectId,sessionId,page,size));
    }
}
