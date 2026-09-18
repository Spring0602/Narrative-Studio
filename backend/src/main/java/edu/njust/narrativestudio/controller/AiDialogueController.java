package edu.njust.narrativestudio.controller;

import edu.njust.narrativestudio.common.ApiResponse;
import edu.njust.narrativestudio.config.CurrentUser;
import edu.njust.narrativestudio.service.AiDialogueService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/ai/dialogue-candidates")
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
