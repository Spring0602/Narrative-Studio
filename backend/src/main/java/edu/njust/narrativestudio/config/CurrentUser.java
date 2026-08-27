package edu.njust.narrativestudio.config;

import edu.njust.narrativestudio.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    public Long id(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtService.AuthenticatedUser user)) {
            throw new BusinessException("UNAUTHORIZED", "请先登录", HttpStatus.UNAUTHORIZED);
        }
        return user.userId();
    }
}
