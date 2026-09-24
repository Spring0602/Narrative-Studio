package edu.njust.narrativestudio.dto;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class ProjectSaveDtos {
    private ProjectSaveDtos() {}
    public record SaveRequest(@Size(max=100) String label,@NotNull JsonNode drafts,
                              @NotNull @Min(0) Long expectedRevision,@Pattern(regexp="[a-f0-9]{64}") String baselineHash) {}
    public record Summary(Long id,Long savedBy,String kind,long revision,String label,String hash,LocalDateTime savedAt) {}
    public record State(List<Summary> manual,Summary automatic,String currentHash) {}
    public record Detail(Summary summary,JsonNode snapshot) {}
    public record SaveResult(boolean changed,Summary saved) {}
    public record RestoreRequest(@NotNull @AssertTrue Boolean confirm,@NotNull @Min(1) Long expectedRevision,
                                 @NotBlank @Size(max=100) String name) {}
    public record Restored(ProjectDtos.Summary project,JsonNode drafts) {}
}
