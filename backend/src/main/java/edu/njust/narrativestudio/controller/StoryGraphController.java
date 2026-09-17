package edu.njust.narrativestudio.controller;

import edu.njust.narrativestudio.common.ApiResponse;
import edu.njust.narrativestudio.config.CurrentUser;
import edu.njust.narrativestudio.dto.StoryGraphDtos;
import edu.njust.narrativestudio.service.StoryGraphService;
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
@RequestMapping("/api/projects/{projectId}/story-nodes")
public class StoryGraphController {
    private final StoryGraphService graphService;
    private final CurrentUser currentUser;

    public StoryGraphController(StoryGraphService graphService, CurrentUser currentUser) {
        this.graphService = graphService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ApiResponse<StoryGraphDtos.Graph> getGraph(Authentication auth, @PathVariable Long projectId) {
        return ApiResponse.ok(graphService.getGraph(currentUser.id(auth), projectId));
    }

    @GetMapping("/{nodeId}")
    public ApiResponse<StoryGraphDtos.NodeSummary> getNode(Authentication auth, @PathVariable Long projectId,
                                                           @PathVariable Long nodeId) {
        return ApiResponse.ok(graphService.getNode(currentUser.id(auth), projectId, nodeId));
    }

    @PostMapping
    public ApiResponse<StoryGraphDtos.NodeSummary> createNode(Authentication auth, @PathVariable Long projectId,
                                                              @Valid @RequestBody StoryGraphDtos.NodeRequest request) {
        return ApiResponse.ok(graphService.createNode(currentUser.id(auth), projectId, request));
    }

    @PostMapping("/batch")
    public ApiResponse<List<StoryGraphDtos.NodeSummary>> createNodes(
            Authentication auth, @PathVariable Long projectId,
            @Valid @RequestBody StoryGraphDtos.BatchNodesRequest request) {
        return ApiResponse.ok(graphService.createNodes(currentUser.id(auth), projectId, request));
    }

    @PutMapping("/{nodeId}")
    public ApiResponse<StoryGraphDtos.NodeSummary> updateNode(Authentication auth, @PathVariable Long projectId,
                                                              @PathVariable Long nodeId,
                                                              @Valid @RequestBody StoryGraphDtos.NodeRequest request) {
        return ApiResponse.ok(graphService.updateNode(currentUser.id(auth), projectId, nodeId, request));
    }

    @DeleteMapping("/{nodeId}")
    public ApiResponse<Void> deleteNode(Authentication auth, @PathVariable Long projectId,
                                        @PathVariable Long nodeId) {
        graphService.deleteNode(currentUser.id(auth), projectId, nodeId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/positions")
    public ApiResponse<Void> updatePositions(Authentication auth, @PathVariable Long projectId,
                                             @Valid @RequestBody StoryGraphDtos.BatchPositionsRequest request) {
        graphService.updatePositions(currentUser.id(auth), projectId, request);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{nodeId}/choices")
    public ApiResponse<List<StoryGraphDtos.ChoiceSummary>> listChoices(Authentication auth,
                                                                       @PathVariable Long projectId,
                                                                       @PathVariable Long nodeId) {
        return ApiResponse.ok(graphService.listChoices(currentUser.id(auth), projectId, nodeId));
    }

    @PostMapping("/{nodeId}/choices")
    public ApiResponse<StoryGraphDtos.ChoiceSummary> createChoice(Authentication auth,
                                                                  @PathVariable Long projectId,
                                                                  @PathVariable Long nodeId,
                                                                  @Valid @RequestBody StoryGraphDtos.ChoiceRequest request) {
        return ApiResponse.ok(graphService.createChoice(currentUser.id(auth), projectId, nodeId, request));
    }

    @PutMapping("/{nodeId}/choices/{choiceId}")
    public ApiResponse<StoryGraphDtos.ChoiceSummary> updateChoice(Authentication auth,
                                                                  @PathVariable Long projectId,
                                                                  @PathVariable Long nodeId,
                                                                  @PathVariable Long choiceId,
                                                                  @Valid @RequestBody StoryGraphDtos.ChoiceRequest request) {
        return ApiResponse.ok(graphService.updateChoice(currentUser.id(auth), projectId, nodeId, choiceId, request));
    }

    @DeleteMapping("/{nodeId}/choices/{choiceId}")
    public ApiResponse<Void> deleteChoice(Authentication auth, @PathVariable Long projectId,
                                          @PathVariable Long nodeId, @PathVariable Long choiceId) {
        graphService.deleteChoice(currentUser.id(auth), projectId, nodeId, choiceId);
        return ApiResponse.ok(null);
    }
}
