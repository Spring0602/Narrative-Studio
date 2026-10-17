package edu.njust.narrativestudio.controller;

import edu.njust.narrativestudio.common.ApiResponse;
import edu.njust.narrativestudio.config.CurrentUser;
import edu.njust.narrativestudio.dto.WorldEntryDtos;
import edu.njust.narrativestudio.service.WorldEntryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
// 答辩修改点：这是整个类的公共地址；下面的方法映射会接在它后面。
// 修改地址时，前端请求地址和接口测试也要同步，不要只修改后端注解。
@RequestMapping("/api/projects/{projectId}/world-entries")
public class WorldEntryController {
    private final WorldEntryService entryService;
    private final CurrentUser currentUser;

    public WorldEntryController(WorldEntryService entryService, CurrentUser currentUser) {
        this.entryService = entryService;
        this.currentUser = currentUser;
    }

    // 【增加标题搜索】用下面的方法替换当前 list（不要保留两个相同路由）：
    // @GetMapping
    // public ApiResponse<List<WorldEntryDtos.Summary>> list(Authentication auth,
    //         @PathVariable Long projectId,
    //         @RequestParam(required = false) String entryType,
    //         @RequestParam(required = false) String keyword) {
    //     return ApiResponse.ok(entryService.list(currentUser.id(auth), projectId, entryType, keyword));
    // }
    // 同步修改 Service 接口和实现类 list 的参数，并在实现类加入搜索条件。
    // required = false 表示不传也可以；不传时通常为 null，由 Service 处理。
    // 当前用户 ID 必须从认证信息取得，不能让前端传 userId 来决定自己是谁。
    @GetMapping
    public ApiResponse<List<WorldEntryDtos.Summary>> list(Authentication auth, @PathVariable Long projectId,
                                                          @RequestParam(required = false) String entryType) {
        return ApiResponse.ok(entryService.list(currentUser.id(auth), projectId, entryType));
    }

    @GetMapping("/{entryId}")
    public ApiResponse<WorldEntryDtos.Summary> get(Authentication auth, @PathVariable Long projectId,
                                                   @PathVariable Long entryId) {
        return ApiResponse.ok(entryService.get(currentUser.id(auth), projectId, entryId));
    }

    // 【标题长度、必填、类型范围】优先修改 SaveRequest 的校验注解。
    // @RequestBody 把 JSON 读成请求对象，@Valid 才会触发 DTO 的基本校验；两者不要混淆。
    // 新增和修改都使用 SaveRequest，所以修改规则后要分别测试两个接口。
    @PostMapping
    public ApiResponse<WorldEntryDtos.Summary> create(Authentication auth, @PathVariable Long projectId,
                                                      @Valid @RequestBody WorldEntryDtos.SaveRequest request) {
        return ApiResponse.ok(entryService.create(currentUser.id(auth), projectId, request));
    }

    // 【部分修改】当前请求仍要提供 DTO 要求的必填字段，并不是“只传一个字段就改一个”。
    // 仅把 @PutMapping 改成 @PatchMapping 不会自动实现部分修改；
    // 真要支持，必须重新设计请求校验及 Service 中哪些字段才覆盖旧值的规则。
    @PutMapping("/{entryId}")
    public ApiResponse<WorldEntryDtos.Summary> update(Authentication auth, @PathVariable Long projectId,
                                                      @PathVariable Long entryId,
                                                      @Valid @RequestBody WorldEntryDtos.SaveRequest request) {
        return ApiResponse.ok(entryService.update(currentUser.id(auth), projectId, entryId, request));
    }

    // 【只允许项目所有者删除】应修改 ServiceImpl 的删除权限检查，见该文件对应注释。
    // 不要在这里取消身份获取，也不要因为前端隐藏按钮就省略服务器权限检查。
    // ApiResponse<Void> 表示成功但没有业务数据，不是没有 HTTP 响应。
    @DeleteMapping("/{entryId}")
    public ApiResponse<Void> delete(Authentication auth, @PathVariable Long projectId,
                                    @PathVariable Long entryId) {
        entryService.delete(currentUser.id(auth), projectId, entryId);
        return ApiResponse.ok(null);
    }
}
