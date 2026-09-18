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
