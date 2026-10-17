package edu.njust.narrativestudio.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;

public final class ExcelImportDtos {
    // 【解释】列下标从 0 开始，表头行号从 1 开始。Mapping 是配置，不是数据库实体。
    // Branch 指定一组“跳转目标列 + 可选的选项文本列”；节点表可有多组分支。
    private ExcelImportDtos() {}
    // Column indexes are zero-based; header rows are one-based. Names are never used as array keys.
    public record Branch(@NotNull @Min(0) @Max(63) Integer targetColumn,
                         @Min(0) @Max(63) Integer textColumn) {}
    // columns 例：{"nodeKey":0,"title":1}，表示第 1 列作编号、第 2 列作标题，表头文字可不同。
    // choiceColumns 用于独立连线表；branches 用于单表分支，解析器禁止两种关系方式同时使用。
    // typeMappings 将自定义类型名称转为 NORMAL/ENDING；preserveExtra 决定是否把节点表补充列拼入正文。
    // 【常见修改】最多 24 组分支若要缩为 12，把 branches 的 @Size(max=24) 改为 @Size(max=12)。
    // 同步前端添加分支按钮限制和测试；扩大时还受 64 列上限与列不可重复使用规则约束。
    // 调整 headerRow 范围时，choiceHeaderRow、inspect 样例范围和前端行号选择也需检查。
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
    // 【常见修改：导入名称最多 50 字符】改为 @Size(max=50, message="项目名称不能超过50个字符")。
    // 同步前端输入限制及测试；只影响 Excel 请求入口，不等于全系统项目名称上限都改了。
    // expectedDigest 是预览结果摘要，confirm 是明确确认；两个字段在真正导入时由 confirmed 检查。
    public record Request(@NotBlank @Size(max=100) String name,
                          @Size(max=1000) String description,
                          @NotNull @Valid Mapping mapping,
                          @Size(max=64) String expectedDigest, Boolean confirm) {}
    // 返回结构：SheetInfo 是表格概览，Preview 是剧情文档+摘要+警告；不是保存后的数据库实体。
    public record SheetInfo(String name,int rowCount,List<List<String>> sampleRows,boolean hidden) {}
    public record Inspection(List<SheetInfo> sheets) {}
    public record Preview(StoryTransferDtos.Document document,String digest,List<String> warnings) {}
}
