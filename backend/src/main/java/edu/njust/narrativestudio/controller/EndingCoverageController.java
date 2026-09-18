package edu.njust.narrativestudio.controller;

import edu.njust.narrativestudio.common.ApiResponse;
import edu.njust.narrativestudio.config.CurrentUser;
import edu.njust.narrativestudio.service.EndingCoverageService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/ending-coverage")
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
