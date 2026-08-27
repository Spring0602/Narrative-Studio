package edu.njust.narrativestudio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{4,32}$", message = "须为4-32位字母、数字或下划线") String username,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 40) String displayName) {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record AuthResponse(String token, Long userId, String username, String displayName) {}
}
