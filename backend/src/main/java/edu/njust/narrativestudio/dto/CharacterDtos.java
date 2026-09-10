package edu.njust.narrativestudio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class CharacterDtos {
    private CharacterDtos() {}

    public record SaveRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 500) String summary,
            @Size(max = 5000) String personality,
            @Size(max = 5000) String goal,
            @Size(max = 5000) String valueOrder) {}

    public record Summary(Long id, Long projectId, String name, String summary,
                          String personality, String goal, String valueOrder,
                          String status, LocalDateTime updatedAt) {}
}
