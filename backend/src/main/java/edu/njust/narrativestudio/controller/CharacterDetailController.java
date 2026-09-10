package edu.njust.narrativestudio.controller;
import edu.njust.narrativestudio.common.ApiResponse;
import edu.njust.narrativestudio.config.CurrentUser;
import edu.njust.narrativestudio.dto.DatabaseDtos.*;
import edu.njust.narrativestudio.entity.*;
import edu.njust.narrativestudio.service.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/projects/{projectId}")
public class CharacterDetailController {
    private final CharacterDetailService service;private final CurrentUser user;
    public CharacterDetailController(CharacterDetailService service,CurrentUser user) {this.service=service;this.user=user;}
    @GetMapping("/character-relations")
    public ApiResponse<List<CharacterRelation>> relations(Authentication auth,@PathVariable Long projectId) {
        return ApiResponse.ok(service.relations(user.id(auth),projectId));
    }
    @GetMapping("/character-relations/{id}")
    public ApiResponse<CharacterRelation> relation(Authentication auth,@PathVariable Long projectId,@PathVariable Long id) {
        return ApiResponse.ok(service.relation(user.id(auth),projectId,id));
    }
    @PostMapping("/character-relations")
    public ApiResponse<CharacterRelation> createRelation(Authentication auth,@PathVariable Long projectId,@Valid @RequestBody RelationRequest request) {
        return ApiResponse.ok(service.saveRelation(user.id(auth),projectId,null,request));
    }
    @PutMapping("/character-relations/{id}")
    public ApiResponse<CharacterRelation> updateRelation(Authentication auth,@PathVariable Long projectId,@PathVariable Long id,@Valid @RequestBody RelationRequest request) {
        return ApiResponse.ok(service.saveRelation(user.id(auth),projectId,id,request));
    }
    @GetMapping("/characters/{characterId}/knowledge")
    public ApiResponse<List<CharacterKnowledge>> knowledge(Authentication auth,@PathVariable Long projectId,@PathVariable Long characterId) {
        return ApiResponse.ok(service.knowledge(user.id(auth),projectId,characterId));
    }
    @GetMapping("/characters/{characterId}/knowledge/{id}")
    public ApiResponse<CharacterKnowledge> knowledgeItem(Authentication auth,@PathVariable Long projectId,@PathVariable Long characterId,@PathVariable Long id) {
        return ApiResponse.ok(service.knowledgeItem(user.id(auth),projectId,characterId,id));
    }
    @PostMapping("/characters/{characterId}/knowledge")
    public ApiResponse<CharacterKnowledge> createKnowledge(Authentication auth,@PathVariable Long projectId,@PathVariable Long characterId,@Valid @RequestBody KnowledgeRequest request) {
        return ApiResponse.ok(service.saveKnowledge(user.id(auth),projectId,characterId,null,request));
    }
    @PutMapping("/characters/{characterId}/knowledge/{id}")
    public ApiResponse<CharacterKnowledge> updateKnowledge(Authentication auth,@PathVariable Long projectId,@PathVariable Long characterId,@PathVariable Long id,@Valid @RequestBody KnowledgeRequest request) {
        return ApiResponse.ok(service.saveKnowledge(user.id(auth),projectId,characterId,id,request));
    }
    @GetMapping("/story-nodes/{nodeId}/characters")
    public ApiResponse<List<Long>> cast(Authentication auth,@PathVariable Long projectId,@PathVariable Long nodeId) {
        return ApiResponse.ok(service.cast(user.id(auth),projectId,nodeId));
    }
    @PutMapping("/story-nodes/{nodeId}/characters")
    public ApiResponse<List<Long>> replaceCast(Authentication auth,@PathVariable Long projectId,@PathVariable Long nodeId,@Valid @RequestBody CastRequest request) {
        return ApiResponse.ok(service.replaceCast(user.id(auth),projectId,nodeId,request));
    }
    @DeleteMapping("/character-relations/{id}")
    public ApiResponse<Void> deleteRelation(Authentication auth,@PathVariable Long projectId,@PathVariable Long id) {
        service.deleteRelation(user.id(auth),projectId,id);return ApiResponse.ok(null);
    }
    @DeleteMapping("/characters/{characterId}/knowledge/{id}")
    public ApiResponse<Void> deleteKnowledge(Authentication auth,@PathVariable Long projectId,@PathVariable Long characterId,@PathVariable Long id) {
        service.deleteKnowledge(user.id(auth),projectId,characterId,id);return ApiResponse.ok(null);
    }
}
