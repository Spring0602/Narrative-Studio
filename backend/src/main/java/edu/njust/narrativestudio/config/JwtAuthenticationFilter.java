package edu.njust.narrativestudio.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
// 【认证链路】读取 Authorization: Bearer ...，校验 JWT，再检查用户 ACTIVE 和 tokenVersion，成功才设置 SecurityContext。
// 失败时清理身份，再交后续安全链决定响应；CurrentUser 从这里建立的身份取用户 ID。
// 【修改提示】不要只解码 JWT 不验签，也不能取消数据库版本检查，否则改密/禁用后旧令牌仍可用。
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final edu.njust.narrativestudio.mapper.UserMapper users;

    public JwtAuthenticationFilter(JwtService jwtService,edu.njust.narrativestudio.mapper.UserMapper users) {
        this.jwtService = jwtService;
        this.users=users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                JwtService.AuthenticatedUser user = jwtService.parse(header.substring(7));
                var current=users.selectById(user.userId());
                if(current==null || !"ACTIVE".equals(current.getStatus()) ||
                    user.tokenVersion()!=(current.getTokenVersion()==null?0L:current.getTokenVersion()))
                    throw new IllegalArgumentException("Revoked token");
                var authentication = new UsernamePasswordAuthenticationToken(user, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
