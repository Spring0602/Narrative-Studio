package edu.njust.narrativestudio.controller;

import edu.njust.narrativestudio.common.ApiResponse;
import edu.njust.narrativestudio.config.CurrentUser;
import edu.njust.narrativestudio.dto.WorldEntryDtos;
import edu.njust.narrativestudio.service.WorldEntryService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/world-entries")
public class WorldEntryController {
    private final WorldEntryService entryService;
    private final CurrentUser currentUser;

    public WorldEntryController(WorldEntryService entryService, CurrentUser currentUser) {
        this.entryService = entryService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ApiResponse<List<WorldEntryDtos.Summary>> list(Authentication auth, @PathVariable Long projectId,
                                                          @RequestParam(required = false) String entryType) {
        return ApiResponse.ok(entryService.list(currentUser.id(auth), projectId, entryType));
    }

    @GetMapping("/{entryId}")
    public ApiResponse<WorldEntryDtos.Summary> get(Authentication auth, @PathVariable Long projectId,
                                                   @PathVariable Long entryId) {
        return ApiResponse.ok(entryService.get(currentUser.id(auth), projectId, entryId));
    }

    @PostMapping
    public ApiResponse<WorldEntryDtos.Summary> create(Authentication auth, @PathVariable Long projectId,
                                                      @Valid @RequestBody WorldEntryDtos.SaveRequest request) {
        return ApiResponse.ok(entryService.create(currentUser.id(auth), projectId, request));
    }

    @PutMapping("/{entryId}")
    public ApiResponse<WorldEntryDtos.Summary> update(Authentication auth, @PathVariable Long projectId,
                                                      @PathVariable Long entryId,
                                                      @Valid @RequestBody WorldEntryDtos.SaveRequest request) {
        return ApiResponse.ok(entryService.update(currentUser.id(auth), projectId, entryId, request));
    }

    @DeleteMapping("/{entryId}")
    public ApiResponse<Void> delete(Authentication auth, @PathVariable Long projectId,
                                    @PathVariable Long entryId) {
        entryService.delete(currentUser.id(auth), projectId, entryId);
        return ApiResponse.ok(null);
    }
}
