package edu.njust.narrativestudio.config;

import edu.njust.narrativestudio.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
// 【当前用户】id() 从 Spring Security 的认证主体取可信用户 ID，未认证抛 401。
// 【修改提示】错误文案可改；不能为调试返回固定 ID 或接受前端自报用户 ID，那会绕过身份隔离。
public class CurrentUser {
    public Long id(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtService.AuthenticatedUser user)) {
            throw new BusinessException("UNAUTHORIZED", "请先登录", HttpStatus.UNAUTHORIZED);
        }
        return user.userId();
    }
}
