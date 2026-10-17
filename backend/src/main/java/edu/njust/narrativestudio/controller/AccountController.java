package edu.njust.narrativestudio.controller;
import edu.njust.narrativestudio.common.ApiResponse;
import edu.njust.narrativestudio.config.CurrentUser;
import edu.njust.narrativestudio.dto.AccountDtos.*;
import edu.njust.narrativestudio.service.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api")
// 【MVC 入口】profile 查资料；requestEmail/confirm 绑定邮箱；requestReset/reset 重置密码；password 改密；users/status 管理员管理用户。accepted 统一公共响应防账户枚举。
// 【修改提示】路由、参数名变化需同步前端 API 和测试；业务权限/关联校验在 Service 保留，不在这里绕过。
// 【常见修改】默认每页 10：此类 size 的 defaultValue="20" 改为 "10"；前端显式传 size 时还需改前端，上限由 Service/FeatureScope 控制。
public class AccountController {
    private final AccountService service;private final CurrentUser user;
    public AccountController(AccountService service,CurrentUser user) {this.service=service;this.user=user;}
    @GetMapping("/account")
    public ApiResponse<Profile> profile(Authentication auth) {return ApiResponse.ok(service.profile(user.id(auth)));}
    @PostMapping("/account/email-verifications")
    public ApiResponse<String> requestEmail(Authentication auth,@Valid @RequestBody EmailRequest r) {
        service.requestEmail(user.id(auth),r);return accepted();
    }
    @PostMapping("/auth/email-verifications/confirm")
    public ApiResponse<Void> confirm(@Valid @RequestBody TokenRequest r) {service.confirmEmail(r);return ApiResponse.ok(null);}
    @PostMapping("/auth/password-resets")
    public ApiResponse<String> requestReset(@Valid @RequestBody ResetRequest r) {service.requestReset(r);return accepted();}
    @PostMapping("/auth/password-resets/confirm")
    public ApiResponse<Void> reset(@Valid @RequestBody ResetConfirm r) {service.reset(r);return ApiResponse.ok(null);}
    @PutMapping("/account/password")
    public ApiResponse<Void> password(Authentication auth,@Valid @RequestBody PasswordRequest r) {
        service.changePassword(user.id(auth),r);return ApiResponse.ok(null);
    }
    @GetMapping("/admin/users")
    public ApiResponse<PlaytestService.Page<Profile>> users(Authentication auth,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.ok(service.users(user.id(auth),page,size));
    }
    @PutMapping("/admin/users/{id}/status")
    public ApiResponse<Profile> status(Authentication auth,@PathVariable Long id,@Valid @RequestBody UserStatus r) {
        return ApiResponse.ok(service.status(user.id(auth),id,r));
    }
    private ApiResponse<String> accepted() {return ApiResponse.ok("若账户符合条件，操作邮件将发送；请勿重复请求。");}
}
