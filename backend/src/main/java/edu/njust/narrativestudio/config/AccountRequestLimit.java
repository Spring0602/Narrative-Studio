package edu.njust.narrativestudio.config;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
/** Single-instance safety net. Uses the actual peer address; never trusts arbitrary forwarding headers. */
@Component
// 【限流链路】shouldNotFilter 选出账户敏感接口；doFilterInternal 超限返回 429；allow 用同步方法维护单机每 IP 时间窗口。
// 【常见修改】每分钟 10 次：allow 中 w.count()>=20 改为 >=10，测试第 10 次成功、第 11 次拒绝。
// 若改时间窗 60 秒，要同步纳秒常量和 Retry-After；这不是跨多实例的共享限流，不信任随意传入的转发 IP 头。
public class AccountRequestLimit extends OncePerRequestFilter {
    private record Window(long start,int count) {}
    private final Map<String,Window> windows=new HashMap<>();
    private long cleaned;
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !Set.of("/api/auth/password-resets","/api/auth/password-resets/confirm",
            "/api/auth/email-verifications/confirm","/api/account/email-verifications").contains(request.getServletPath());
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        if(!allow(request.getRemoteAddr())) {
            response.setStatus(429);response.setHeader("Retry-After","60");response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"请求过于频繁，请稍后重试\"}}");return;
        }
        chain.doFilter(request,response);
    }
    private synchronized boolean allow(String peer) {
        long now=System.nanoTime();long minute=60_000_000_000L;
        if(now-cleaned>minute) {windows.entrySet().removeIf(e->now-e.getValue().start()>=minute);cleaned=now;}
        Window w=windows.get(peer);
        if(w==null || now-w.start()>=minute) {
            if(w==null && windows.size()>=4096) return false;
            windows.put(peer,new Window(now,1));return true;
        }
        if(w.count()>=20) return false;
        windows.put(peer,new Window(w.start(),w.count()+1));return true;
    }
}
