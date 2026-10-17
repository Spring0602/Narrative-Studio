package edu.njust.narrativestudio.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
// 【令牌链路】createToken 写入用户、版本及到期时间并签名；parse 校验签名/有效期并提取身份，数据库状态由过滤器再核对。
// 【常见修改】有效期在 app.jwt.expiration-seconds 配置，单位秒，例如 3600 表示一小时；不在 parse 中忽略过期异常。
// 两参数 createToken 默认版本 0，正式登录应使用当前 tokenVersion；JWT 不是加密保险箱，不放密码或私密正文。
public class JwtService {
    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-seconds}") long expirationSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    public String createToken(Long userId, String username) {
        return createToken(userId,username,0L);
    }
    public String createToken(Long userId,String username,Long version) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("tokenVersion",version==null?0L:version)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(key)
                .compact();
    }

    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        Number version=claims.get("tokenVersion",Number.class);
        return new AuthenticatedUser(Long.valueOf(claims.getSubject()), claims.get("username", String.class),version==null?0:version.longValue());
    }

    public record AuthenticatedUser(Long userId, String username,long tokenVersion) {}
}
