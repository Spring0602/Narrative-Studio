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
