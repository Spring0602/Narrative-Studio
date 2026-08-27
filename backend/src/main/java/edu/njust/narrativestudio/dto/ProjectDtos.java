package edu.njust.narrativestudio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class ProjectDtos {
    private ProjectDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 1000) String description) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 1000) String description) {}

    public record Summary(Long id, String name, String description, Long ownerId,
                          String status, String memberRole, LocalDateTime updatedAt) {}
}
