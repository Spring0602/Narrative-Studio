package edu.njust.narrativestudio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

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
