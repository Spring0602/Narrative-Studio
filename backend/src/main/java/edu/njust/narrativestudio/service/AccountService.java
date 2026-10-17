package edu.njust.narrativestudio.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import edu.njust.narrativestudio.dto.AccountDtos.*;
import edu.njust.narrativestudio.entity.*;
import edu.njust.narrativestudio.mapper.*;
import edu.njust.narrativestudio.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class AccountService {
    private final UserMapper users;private final AccountActionTokenMapper tokens;private final PasswordEncoder passwords;private final AccountMailSender mail;
    private final SecureRandom random=new SecureRandom();
    public AccountService(UserMapper users,AccountActionTokenMapper tokens,PasswordEncoder passwords,AccountMailSender mail) {
        this.users=users;this.tokens=tokens;this.passwords=passwords;this.mail=mail;
    }
    // 【解释】查询有效账户并转为 Profile，不返回密码摘要。
    public Profile profile(Long id) { return view(active(users.selectById(id))); }
    @Transactional
    // 【解释】确认邮件配置、锁用户并验证当前密码，再签发绑定邮箱的一次性凭据。
    public void requestEmail(Long id,EmailRequest r) {
        mail.requireConfigured();User user=active(users.lockById(id));checkPassword(r.password(),user);
        issue(user,"VERIFY_EMAIL",normalize(r.email()));
    }
    @Transactional
    // 【解释】验证并消费凭据，设置邮箱与验证时间，递增 tokenVersion 使旧 JWT 失效；唯一邮箱约束冲突时事务回滚。
    public void confirmEmail(TokenRequest r) {
        AccountActionToken t=validToken(r.token(),"VERIFY_EMAIL");User user=active(users.lockById(t.getUserId()));
        user.setEmail(t.getDestinationEmail());user.setEmailVerifiedAt(LocalDateTime.now());bump(user);
        // UNIQUE(email) is the final concurrent ownership check. A conflict rolls back token consumption too.
        users.updateById(user);invalidate(user.getId());
    }
    @Transactional
    // 【解释】仅给已验证且有效的邮箱发重置凭据；未知邮箱也正常返回，避免泄露账户是否存在。
    public void requestReset(ResetRequest r) {
        mail.requireConfigured();
        User found=users.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail,normalize(r.email())));
        if(found==null) return;
        User user=users.lockById(found.getId());
        if(user==null || !"ACTIVE".equals(user.getStatus()) || user.getEmailVerifiedAt()==null || !normalize(r.email()).equals(user.getEmail())) return;
        try { issue(user,"RESET_PASSWORD",user.getEmail()); }
        catch(BusinessException ex) {
            if(!"MAIL_UNAVAILABLE".equals(ex.getCode())) throw ex;
            org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            org.slf4j.LoggerFactory.getLogger(AccountService.class).warn("Password reset mail delivery failed; credential transaction rolled back");
            // Keep the public response identical for existing and nonexistent accounts on transport failure.
        }
    }
    @Transactional
    // 【解释】凭据必须未用、未过期、用途匹配且仍对应当前已验证邮箱，再设置新密码。
    public void reset(ResetConfirm r) {
        AccountActionToken t=validToken(r.token(),"RESET_PASSWORD");User user=active(users.lockById(t.getUserId()));
        if(user.getEmailVerifiedAt()==null || !Objects.equals(t.getDestinationEmail(),user.getEmail())) throw invalidToken();
        setPassword(user,r.password());
    }
    @Transactional
    // 【解释】登录用户先验证旧密码，再设置新密码并撤销旧令牌。
    public void changePassword(Long id,PasswordRequest r) {
        User user=active(users.lockById(id));checkPassword(r.currentPassword(),user);setPassword(user,r.newPassword());
    }
    // 【解释】校验 BCrypt 的 72 字节限制，编码密码，递增令牌版本并作废一次性凭据。
    // 【常见修改】新密码至少 10 字符：同步 AuthDtos.RegisterRequest、AccountDtos.ResetConfirm/PasswordRequest 的最短长度，不改旧密码验证规则。
    private void setPassword(User user,String password) {
        if(password.getBytes(StandardCharsets.UTF_8).length>72) throw FeatureScope.invalid("密码的UTF-8长度不能超过72字节");
        user.setPasswordHash(passwords.encode(password));bump(user);users.updateById(user);invalidate(user.getId());
    }
    // 【解释】更新 tokenVersion 和修改时间；过滤器会比对版本，不能为保持登录而随便删除此调用。
    private void bump(User user) { user.setTokenVersion(Math.incrementExact(user.getTokenVersion()==null?0:user.getTokenVersion()));user.setUpdatedAt(LocalDateTime.now()); }
    // 【解释】用户锁下限频，随机生成凭据，只保存摘要，设置有效期并发邮件。
    // 【常见修改】有效期 30 分钟：plusMinutes(15) 改为 plusMinutes(30)，同步 AccountMailSender 的“15分钟”文案及过期边界测试。
    // minusSeconds(60) 是用户级发信间隔，AccountRequestLimit 是另一个 IP 限流；不应混为一处。
    private void issue(User user,String purpose,String destination) {
        // User row lock serializes both rate limiting and issuance. Public requests remain enumeration-neutral.
        var recent=tokens.selectCount(new LambdaQueryWrapper<AccountActionToken>().eq(AccountActionToken::getUserId,user.getId())
            .gt(AccountActionToken::getCreatedAt,LocalDateTime.now().minusSeconds(60)));
        if(recent>0) return;
        invalidate(user.getId());
        byte[] bytes=new byte[32];random.nextBytes(bytes);String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        AccountActionToken t=new AccountActionToken();t.setUserId(user.getId());t.setPurpose(purpose);t.setTokenHash(hash(raw));
        t.setDestinationEmail(destination);t.setCreatedAt(LocalDateTime.now());t.setExpiresAt(t.getCreatedAt().plusMinutes(15));tokens.insert(t);
        mail.send(destination,purpose,raw); // Never returned by HTTP or written to logs.
    }
    // 【解释】按摘要找凭据，按用户->凭据固定顺序加锁，检查用途/过期/已使用后标记已消费。
    private AccountActionToken validToken(String raw,String purpose) {
        AccountActionToken found=tokens.find(hash(raw));if(found==null) throw invalidToken();
        active(users.lockById(found.getUserId())); // Consistent user -> token lock order, including parallel confirmations.
        AccountActionToken t=tokens.lock(found.getId());
        if(t==null || t.getUsedAt()!=null || !purpose.equals(t.getPurpose()) || !t.getExpiresAt().isAfter(LocalDateTime.now())) throw invalidToken();
        t.setUsedAt(LocalDateTime.now());tokens.updateById(t);return t;
    }
    // 【解释】将该用户尚未使用的操作凭据标记失效，不是删除用户。
    private void invalidate(Long user) {
        tokens.update(null,new LambdaUpdateWrapper<AccountActionToken>().eq(AccountActionToken::getUserId,user)
            .isNull(AccountActionToken::getUsedAt).set(AccountActionToken::getUsedAt,LocalDateTime.now()));
    }
    // 【解释】凭据计算 SHA-256 摘要用于查找；不是密码存储算法，也不能从摘要恢复凭据。
    private byte[] hash(String raw) {
        try { return MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)); }
        catch(NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    // 【解释】邮箱去首尾空白并统一小写；更改规范化规则需核对数据库唯一性与旧数据。
    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    // 【解释】拒绝不存在和非 ACTIVE 用户。
    private User active(User user) {
        if(user==null || !"ACTIVE".equals(user.getStatus())) throw new BusinessException("UNAUTHORIZED","账户不可用",HttpStatus.UNAUTHORIZED);return user;
    }
    // 【解释】用编码器 matches 验证密码，不能把明文直接与摘要 equals。
    private void checkPassword(String password,User user) {
        if(!passwords.matches(password,user.getPasswordHash())) throw new BusinessException("INVALID_CREDENTIALS","当前密码错误",HttpStatus.UNAUTHORIZED);
    }
    // 【解释】对无效、已用、过期统一提示，不泄露凭据细节。
    private BusinessException invalidToken() { return FeatureScope.invalid("凭据无效、已使用或已过期"); }
    // 【解释】将用户实体转安全的个人资料响应。
    private Profile view(User u) { return new Profile(u.getId(),u.getUsername(),u.getDisplayName(),u.getEmail(),u.getEmailVerifiedAt(),u.getRole(),u.getStatus()); }
    // 【解释】仅系统 ADMIN 可分页列用户；项目 OWNER 不是系统管理员。
    public PlaytestService.Page<Profile> users(Long actor,int page,int size) {
        requireAdmin(actor);String limit=FeatureScope.limit(page,size);long total=users.selectCount(null);
        return new PlaytestService.Page<>(users.selectList(new LambdaQueryWrapper<User>().orderByAsc(User::getId).last(limit)).stream().map(this::view).toList(),
            page,size,total,(total+size-1)/size);
    }
    @Transactional
    // 【解释】管理员不能改自己状态；修改其他用户状态后递增令牌版本并作废操作凭据。
    public Profile status(Long actor,Long id,UserStatus r) {
        requireAdmin(actor);if(actor.equals(id)) throw BusinessException.conflict("不能修改自己的账户状态");
        User user=users.lockById(id);if(user==null) throw BusinessException.notFound("用户不存在");
        user.setStatus(r.status());bump(user);users.updateById(user);invalidate(id);return view(user);
    }
    // 【解释】查数据库中的有效账户和 ADMIN 角色，不信任前端传入角色。
    private void requireAdmin(Long id) {
        if(!"ADMIN".equals(active(users.selectById(id)).getRole())) throw BusinessException.forbidden("需要系统管理员权限");
    }
}
