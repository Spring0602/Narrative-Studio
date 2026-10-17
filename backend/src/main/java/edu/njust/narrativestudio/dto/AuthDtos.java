package edu.njust.narrativestudio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 【请求/响应】RegisterRequest 注册，LoginRequest 登录，AuthResponse 返回令牌和基本资料；@Valid 在 Controller 触发。
// 【常见修改】昵称最多 30：displayName 的 @Size(max=40) 改为 30；用户名最短 6 位需把正则 {4,32} 和对应提示一起改。
// 密码最短长度需同步 AccountDtos 的重置/改密新密码规则；旧密码验证不是注册校验。
public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{4,32}$", message = "须为4-32位字母、数字或下划线") String username,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 40) String displayName) {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record AuthResponse(String token, Long userId, String username, String displayName) {}
}
