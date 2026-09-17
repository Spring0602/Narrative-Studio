package edu.njust.narrativestudio.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;

public final class ExcelImportDtos {
    private ExcelImportDtos() {}
    // Column indexes are zero-based; header rows are one-based. Names are never used as array keys.
    public record Branch(@NotNull @Min(0) @Max(63) Integer targetColumn,
                         @Min(0) @Max(63) Integer textColumn) {}
    public record Mapping(
            @NotBlank @Size(max=31) String nodeSheet,
            @Min(1) @Max(30) int headerRow,
            @NotNull @Size(max=6) Map<@NotBlank String,@NotNull @Min(0) @Max(63) Integer> columns,
            @Size(max=31) String choiceSheet,
            @Min(1) @Max(30) int choiceHeaderRow,
            @NotNull @Size(max=3) Map<@NotBlank String,@NotNull @Min(0) @Max(63) Integer> choiceColumns,
            @NotNull @Size(max=24) List<@NotNull @Valid Branch> branches,
            @NotNull @Size(max=64) Map<@NotBlank @Size(max=120) String,@NotNull @Pattern(regexp="NORMAL|ENDING") String> typeMappings,
            @Size(max=64) String startNodeKey,
            boolean preserveExtra) {}
    public record Request(@NotBlank @Size(max=100) String name,
                          @Size(max=1000) String description,
                          @NotNull @Valid Mapping mapping,
                          @Size(max=64) String expectedDigest, Boolean confirm) {}
    public record SheetInfo(String name,int rowCount,List<List<String>> sampleRows,boolean hidden) {}
    public record Inspection(List<SheetInfo> sheets) {}
    public record Preview(StoryTransferDtos.Document document,String digest,List<String> warnings) {}
}
