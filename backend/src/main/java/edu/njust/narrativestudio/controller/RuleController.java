package edu.njust.narrativestudio.controller;
import edu.njust.narrativestudio.common.ApiResponse;
import edu.njust.narrativestudio.config.CurrentUser;
import edu.njust.narrativestudio.dto.RuleDtos.*;
import edu.njust.narrativestudio.service.RuleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class RuleController {
    private final RuleService service;
    private final CurrentUser user;
    public RuleController(RuleService service,CurrentUser user) { this.service=service; this.user=user; }
    @GetMapping("/state-variables")
    public ApiResponse<List<VariableView>> list(Authentication auth,@PathVariable Long projectId) {
        return ApiResponse.ok(service.listVariables(user.id(auth),projectId));
    }
    @GetMapping("/state-variables/{variableId}")
    public ApiResponse<VariableView> get(Authentication auth,@PathVariable Long projectId,@PathVariable Long variableId) {
        return ApiResponse.ok(service.getVariable(user.id(auth),projectId,variableId));
    }
    @PostMapping("/state-variables")
    public ApiResponse<VariableView> create(Authentication auth,@PathVariable Long projectId,@Valid @RequestBody VariableRequest request) {
        return ApiResponse.ok(service.createVariable(user.id(auth),projectId,request));
    }
    @PutMapping("/state-variables/{variableId}")
    public ApiResponse<VariableView> update(Authentication auth,@PathVariable Long projectId,@PathVariable Long variableId,@Valid @RequestBody VariableRequest request) {
        return ApiResponse.ok(service.updateVariable(user.id(auth),projectId,variableId,request));
    }
    @DeleteMapping("/state-variables/{variableId}")
    public ApiResponse<Void> delete(Authentication auth,@PathVariable Long projectId,@PathVariable Long variableId) {
        service.deleteVariable(user.id(auth),projectId,variableId); return ApiResponse.ok(null);
    }
    @GetMapping("/story-nodes/{nodeId}/choices/{choiceId}/rules")
    public ApiResponse<RulesView> rules(Authentication auth,@PathVariable Long projectId,@PathVariable Long nodeId,@PathVariable Long choiceId) {
        return ApiResponse.ok(service.getRules(user.id(auth),projectId,nodeId,choiceId));
    }
    @PutMapping("/story-nodes/{nodeId}/choices/{choiceId}/rules")
    public ApiResponse<RulesView> replace(Authentication auth,@PathVariable Long projectId,@PathVariable Long nodeId,
            @PathVariable Long choiceId,@Valid @RequestBody RulesRequest request) {
        return ApiResponse.ok(service.replaceRules(user.id(auth),projectId,nodeId,choiceId,request));
    }
}
