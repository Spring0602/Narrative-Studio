package edu.njust.narrativestudio.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
// 【账户格式】Profile 不含密码摘要；邮箱请求验证格式；TokenRequest 限定一次性凭据格式；UserStatus 仅 ACTIVE/DISABLED。
// 【常见修改】新密码至少 10：ResetConfirm.password 与 PasswordRequest.newPassword 的 min=8 改为 10，并同步注册及前端。
// 不要改 currentPassword 最短长度来阻止旧密码登录；72 字符还必须通过 Service 的 72 UTF-8 字节检查。
public final class AccountDtos {
    private AccountDtos() {}
    public record Profile(Long id,String username,String displayName,String email,LocalDateTime emailVerifiedAt,String role,String status) {}
    public record EmailRequest(@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(max=72) String password) {}
    public record ResetRequest(@NotBlank @Email @Size(max=254) String email) {}
    public record TokenRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token) {}
    public record ResetConfirm(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token,@NotBlank @Size(min=8,max=72) String password) {}
    public record PasswordRequest(@NotBlank @Size(max=72) String currentPassword,@NotBlank @Size(min=8,max=72) String newPassword) {}
    public record UserStatus(@NotBlank @Pattern(regexp="ACTIVE|DISABLED") String status) {}
}
