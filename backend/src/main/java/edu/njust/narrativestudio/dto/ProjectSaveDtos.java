package edu.njust.narrativestudio.dto;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class ProjectSaveDtos {
    private ProjectSaveDtos() {}
    // 【常见修改 5：备注最多 50 字符】将下面 @Size(max=100) 改为
    // @Size(max=50, message="版本备注不能超过50个字符")，同步前端备注输入框 maxlength 和测试。
    // 数据库 label 当前为 VARCHAR(100)，缩小接口限制不用改列；扩大超过列容量要同步数据库迁移。
    // label 是可选项，不要随意加 @NotBlank：自动保存请求不传 label，也共用这个 DTO。
    // drafts 即使没有表单内容也应传 {}；expectedRevision 的 0 表示还没有自动草稿，不是非法值。
    public record SaveRequest(@Size(max=100) String label,@NotNull JsonNode drafts,
                              @NotNull @Min(0) Long expectedRevision,@Pattern(regexp="[a-f0-9]{64}") String baselineHash) {}
    public record Summary(Long id,Long savedBy,String kind,long revision,String label,String hash,LocalDateTime savedAt) {}
    // 【常见修改 6：返回现有手动记录数】在 State 参数末尾追加 int manualCount，
    // 在 ProjectSaveService.state() 的 new State(...) 末尾追加 manual.size()。
    // 同步所有构造调用、前端类型和测试；这是计算值，无须新增数据库列。
    public record State(List<Summary> manual,Summary automatic,String currentHash) {}
    public record Detail(Summary summary,JsonNode snapshot) {}
    public record SaveResult(boolean changed,Summary saved) {}
    // 【常见修改 7：恢复项目名最多 50 字符】把下一行 name 的 @Size(max=100) 改为 @Size(max=50)。
    // 同步 ProjectSaveBar.vue 的恢复名称 maxlength 和默认名称 slice(0,100) 为 50。
    // 这只缩小“恢复”入口；若要求所有项目名统一限制，还要检查 ProjectDtos 和 ProjectService。
    // 验证 50 字符允许、51 拒绝、空白拒绝；confirm 和 expectedRevision 不要删。
    public record RestoreRequest(@NotNull @AssertTrue Boolean confirm,@NotNull @Min(1) Long expectedRevision,
                                 @NotBlank @Size(max=100) String name) {}
    public record Restored(ProjectDtos.Summary project,JsonNode drafts) {}
}
