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
    // 【链路解释】multipart/form-data 同时传文件 file 和 JSON 配置 options，不能按普通单一 JSON 请求发送。
    // inspect/preview 负责查看与校验，不落库；create/intoEmpty 才会写入项目。
    // 【修改提示】改路由或 RequestPart 名称时同步前端上传代码和接口测试；保留 options 上的 @Valid。
    private final ExcelStoryParser parser;
    private final ExcelImportService imports;
    private final CurrentUser current;
    public ExcelImportController(ExcelStoryParser parser,ExcelImportService imports,CurrentUser current) {
        this.parser=parser;this.imports=imports;this.current=current;
    }
    // 第一步：查看工作表样例，供用户选择工作表、表头和字段对应列。
    @PostMapping(value="/story-excel/inspect",consumes="multipart/form-data")
    public ApiResponse<Inspection> inspect(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(parser.inspect(file));
    }
    // 第二步：按映射生成预览；返回的 digest 在确认导入时作为 expectedDigest 提交。
    @PostMapping(value="/story-excel/preview",consumes="multipart/form-data")
    public ApiResponse<Preview> preview(@RequestPart("file") MultipartFile file,@Valid @RequestPart("options") Request request) {
        return ApiResponse.ok(parser.preview(file,request));
    }
    // 第三步方案 A：创建新项目，当前用户身份来自认证信息，而非上传文件。
    @PostMapping(value="/projects/import-excel",consumes="multipart/form-data")
    public ApiResponse<ProjectDtos.Summary> create(Authentication auth,@RequestPart("file") MultipartFile file,
            @Valid @RequestPart("options") Request request) {
        return ApiResponse.ok(imports.create(current.id(auth),file,request));
    }
    // 第三步方案 B：导入已有项目的空剧情图，由 Service 检查权限、加锁并拒绝覆盖已有节点。
    @PostMapping(value="/projects/{projectId}/import-excel",consumes="multipart/form-data")
    public ApiResponse<ProjectDtos.Summary> intoEmpty(Authentication auth,@PathVariable Long projectId,
            @RequestPart("file") MultipartFile file,@Valid @RequestPart("options") Request request) {
        return ApiResponse.ok(imports.intoEmpty(current.id(auth),projectId,file,request));
    }
}
