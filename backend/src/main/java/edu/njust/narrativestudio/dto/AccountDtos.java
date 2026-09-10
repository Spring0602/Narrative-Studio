package edu.njust.narrativestudio.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
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
