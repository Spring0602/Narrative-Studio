package edu.njust.narrativestudio.controller;

import edu.njust.narrativestudio.common.ApiResponse;
import edu.njust.narrativestudio.config.CurrentUser;
import edu.njust.narrativestudio.dto.CharacterDtos;
import edu.njust.narrativestudio.service.CharacterService;
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
@RequestMapping("/api/projects/{projectId}/characters")
public class CharacterController {
    private final CharacterService characterService;
    private final CurrentUser currentUser;

    public CharacterController(CharacterService characterService, CurrentUser currentUser) {
        this.characterService = characterService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ApiResponse<List<CharacterDtos.Summary>> list(Authentication auth, @PathVariable Long projectId) {
        return ApiResponse.ok(characterService.list(currentUser.id(auth), projectId));
    }

    @GetMapping("/{characterId}")
    public ApiResponse<CharacterDtos.Summary> get(Authentication auth, @PathVariable Long projectId,
                                                  @PathVariable Long characterId) {
        return ApiResponse.ok(characterService.get(currentUser.id(auth), projectId, characterId));
    }

    @PostMapping
    public ApiResponse<CharacterDtos.Summary> create(Authentication auth, @PathVariable Long projectId,
                                                     @Valid @RequestBody CharacterDtos.SaveRequest request) {
        return ApiResponse.ok(characterService.create(currentUser.id(auth), projectId, request));
    }

    @PutMapping("/{characterId}")
    public ApiResponse<CharacterDtos.Summary> update(Authentication auth, @PathVariable Long projectId,
                                                     @PathVariable Long characterId,
                                                     @Valid @RequestBody CharacterDtos.SaveRequest request) {
        return ApiResponse.ok(characterService.update(currentUser.id(auth), projectId, characterId, request));
    }

    @DeleteMapping("/{characterId}")
    public ApiResponse<Void> delete(Authentication auth, @PathVariable Long projectId,
                                    @PathVariable Long characterId) {
        characterService.delete(currentUser.id(auth), projectId, characterId);
        return ApiResponse.ok(null);
    }
}
