package edu.njust.narrativestudio.controller;

import edu.njust.narrativestudio.common.ApiResponse;
import edu.njust.narrativestudio.config.CurrentUser;
import edu.njust.narrativestudio.dto.ExcelImportDtos.*;
import edu.njust.narrativestudio.dto.ProjectDtos;
import edu.njust.narrativestudio.service.ExcelImportService;
import edu.njust.narrativestudio.service.ExcelStoryParser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ExcelImportController {
    private final ExcelStoryParser parser;
    private final ExcelImportService imports;
    private final CurrentUser current;
    public ExcelImportController(ExcelStoryParser parser,ExcelImportService imports,CurrentUser current) {
        this.parser=parser;this.imports=imports;this.current=current;
    }
    @PostMapping(value="/story-excel/inspect",consumes="multipart/form-data")
    public ApiResponse<Inspection> inspect(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(parser.inspect(file));
    }
    @PostMapping(value="/story-excel/preview",consumes="multipart/form-data")
    public ApiResponse<Preview> preview(@RequestPart("file") MultipartFile file,@Valid @RequestPart("options") Request request) {
        return ApiResponse.ok(parser.preview(file,request));
    }
    @PostMapping(value="/projects/import-excel",consumes="multipart/form-data")
    public ApiResponse<ProjectDtos.Summary> create(Authentication auth,@RequestPart("file") MultipartFile file,
            @Valid @RequestPart("options") Request request) {
        return ApiResponse.ok(imports.create(current.id(auth),file,request));
    }
    @PostMapping(value="/projects/{projectId}/import-excel",consumes="multipart/form-data")
    public ApiResponse<ProjectDtos.Summary> intoEmpty(Authentication auth,@PathVariable Long projectId,
            @RequestPart("file") MultipartFile file,@Valid @RequestPart("options") Request request) {
        return ApiResponse.ok(imports.intoEmpty(current.id(auth),projectId,file,request));
    }
}
