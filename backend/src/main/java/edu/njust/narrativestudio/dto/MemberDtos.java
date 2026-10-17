package edu.njust.narrativestudio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

// 【成员格式】AddRequest 指定已有用户名及项目角色；RoleRequest 改角色；Summary 返回成员和用户展示信息。
// 【修改提示】EDITOR|TESTER 白名单有意不开放 OWNER。要改中文角色名只改前端标签，别直接更换存储枚举值。
public final class MemberDtos {
    private MemberDtos() {}

    public record AddRequest(
            @NotBlank @Size(max = 32) String username,
            @NotBlank @Pattern(regexp = "EDITOR|TESTER") String memberRole) {}

    public record RoleRequest(
            @NotBlank @Pattern(regexp = "EDITOR|TESTER") String memberRole) {}

    public record Summary(Long id, Long userId, String username, String displayName,
                          String memberRole, LocalDateTime joinedAt) {}
}
