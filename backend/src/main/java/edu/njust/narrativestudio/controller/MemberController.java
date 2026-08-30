package edu.njust.narrativestudio.controller;

import edu.njust.narrativestudio.common.ApiResponse;
import edu.njust.narrativestudio.config.CurrentUser;
import edu.njust.narrativestudio.dto.MemberDtos;
import edu.njust.narrativestudio.service.MemberService;
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
@RequestMapping("/api/projects/{projectId}/members")
public class MemberController {
    private final MemberService memberService;
    private final CurrentUser currentUser;

    public MemberController(MemberService memberService, CurrentUser currentUser) {
        this.memberService = memberService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ApiResponse<List<MemberDtos.Summary>> list(Authentication auth, @PathVariable Long projectId) {
        return ApiResponse.ok(memberService.list(currentUser.id(auth), projectId));
    }

    @PostMapping
    public ApiResponse<MemberDtos.Summary> add(Authentication auth, @PathVariable Long projectId,
                                                @Valid @RequestBody MemberDtos.AddRequest request) {
        return ApiResponse.ok(memberService.add(currentUser.id(auth), projectId, request));
    }

    @PutMapping("/{memberId}/role")
    public ApiResponse<MemberDtos.Summary> updateRole(Authentication auth, @PathVariable Long projectId,
                                                       @PathVariable Long memberId,
                                                       @Valid @RequestBody MemberDtos.RoleRequest request) {
        return ApiResponse.ok(memberService.updateRole(currentUser.id(auth), projectId, memberId, request));
    }

    @DeleteMapping("/{memberId}")
    public ApiResponse<Void> remove(Authentication auth, @PathVariable Long projectId,
                                    @PathVariable Long memberId) {
        memberService.remove(currentUser.id(auth), projectId, memberId);
        return ApiResponse.ok(null);
    }
}
