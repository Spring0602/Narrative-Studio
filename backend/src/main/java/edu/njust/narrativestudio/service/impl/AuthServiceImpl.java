package edu.njust.narrativestudio.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.njust.narrativestudio.config.JwtService;
import edu.njust.narrativestudio.dto.AuthDtos;
import edu.njust.narrativestudio.entity.User;
import edu.njust.narrativestudio.exception.BusinessException;
import edu.njust.narrativestudio.mapper.UserMapper;
import edu.njust.narrativestudio.service.AuthService;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    // 【解释】检查密码 UTF-8 字节长度及用户名重复，BCrypt 编码后写入用户，再签发令牌；事务保证注册写入完整。
    // 【常见修改】显示名上限改 AuthDtos.RegisterRequest 的 @Size；密码最短长度需同时检查注册、重置、改密 DTO。
    // 不能保存明文密码，也不要取消 72 字节检查；数据库唯一约束仍是并发重名的最后保护。
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw edu.njust.narrativestudio.service.FeatureScope.invalid("密码 UTF-8 编码后不能超过 72 字节");
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, request.username()));
        if (count > 0) throw BusinessException.conflict("用户名已存在");
        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return response(user);
    }

    @Override
    // 【解释】按用户名查询，用 matches 验证密码，再检查 ACTIVE 状态；成功才返回 JWT。
    // 【修改提示】可以改“用户名或密码错误”的文字，不能分别透露用户名是否存在；登录不能复用新密码强度限制阻止旧用户登录。
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("INVALID_CREDENTIALS", "用户名或密码错误", HttpStatus.UNAUTHORIZED);
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException("ACCOUNT_DISABLED", "账户不可用", HttpStatus.FORBIDDEN);
        }
        return response(user);
    }

    // 【解释】统一生成登录/注册响应，JWT 带 tokenVersion；新增返回字段需同步 AuthResponse、前端和构造调用，不返回密码摘要。
    private AuthDtos.AuthResponse response(User user) {
        return new AuthDtos.AuthResponse(jwtService.createToken(user.getId(), user.getUsername(),user.getTokenVersion()),
                user.getId(), user.getUsername(), user.getDisplayName());
    }
}
