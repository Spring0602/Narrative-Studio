package edu.njust.narrativestudio.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class WorldEntryDtos {
    private WorldEntryDtos() {}

    public record SaveRequest(
            @NotBlank @Pattern(regexp = "SETTING|LOCATION|FACTION|HISTORY|RULE|OTHER") String entryType,
            @NotBlank @Size(max = 100) String title,
            @NotBlank @Size(max = 10000) String content,
            @Min(0) Integer sortOrder) {}

    public record Summary(Long id, Long projectId, String entryType, String title,
                          String content, Integer sortOrder, LocalDateTime updatedAt) {}
}
