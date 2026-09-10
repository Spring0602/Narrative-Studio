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
    public Profile profile(Long id) { return view(active(users.selectById(id))); }
    @Transactional
    public void requestEmail(Long id,EmailRequest r) {
        mail.requireConfigured();User user=active(users.lockById(id));checkPassword(r.password(),user);
        issue(user,"VERIFY_EMAIL",normalize(r.email()));
    }
    @Transactional
    public void confirmEmail(TokenRequest r) {
        AccountActionToken t=validToken(r.token(),"VERIFY_EMAIL");User user=active(users.lockById(t.getUserId()));
        user.setEmail(t.getDestinationEmail());user.setEmailVerifiedAt(LocalDateTime.now());bump(user);
        // UNIQUE(email) is the final concurrent ownership check. A conflict rolls back token consumption too.
        users.updateById(user);invalidate(user.getId());
    }
    @Transactional
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
    public void reset(ResetConfirm r) {
        AccountActionToken t=validToken(r.token(),"RESET_PASSWORD");User user=active(users.lockById(t.getUserId()));
        if(user.getEmailVerifiedAt()==null || !Objects.equals(t.getDestinationEmail(),user.getEmail())) throw invalidToken();
        setPassword(user,r.password());
    }
    @Transactional
    public void changePassword(Long id,PasswordRequest r) {
        User user=active(users.lockById(id));checkPassword(r.currentPassword(),user);setPassword(user,r.newPassword());
    }
    private void setPassword(User user,String password) {
        if(password.getBytes(StandardCharsets.UTF_8).length>72) throw FeatureScope.invalid("密码的UTF-8长度不能超过72字节");
        user.setPasswordHash(passwords.encode(password));bump(user);users.updateById(user);invalidate(user.getId());
    }
    private void bump(User user) { user.setTokenVersion(Math.incrementExact(user.getTokenVersion()==null?0:user.getTokenVersion()));user.setUpdatedAt(LocalDateTime.now()); }
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
    private AccountActionToken validToken(String raw,String purpose) {
        AccountActionToken found=tokens.find(hash(raw));if(found==null) throw invalidToken();
        active(users.lockById(found.getUserId())); // Consistent user -> token lock order, including parallel confirmations.
        AccountActionToken t=tokens.lock(found.getId());
        if(t==null || t.getUsedAt()!=null || !purpose.equals(t.getPurpose()) || !t.getExpiresAt().isAfter(LocalDateTime.now())) throw invalidToken();
        t.setUsedAt(LocalDateTime.now());tokens.updateById(t);return t;
    }
    private void invalidate(Long user) {
        tokens.update(null,new LambdaUpdateWrapper<AccountActionToken>().eq(AccountActionToken::getUserId,user)
            .isNull(AccountActionToken::getUsedAt).set(AccountActionToken::getUsedAt,LocalDateTime.now()));
    }
    private byte[] hash(String raw) {
        try { return MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)); }
        catch(NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private User active(User user) {
        if(user==null || !"ACTIVE".equals(user.getStatus())) throw new BusinessException("UNAUTHORIZED","账户不可用",HttpStatus.UNAUTHORIZED);return user;
    }
    private void checkPassword(String password,User user) {
        if(!passwords.matches(password,user.getPasswordHash())) throw new BusinessException("INVALID_CREDENTIALS","当前密码错误",HttpStatus.UNAUTHORIZED);
    }
    private BusinessException invalidToken() { return FeatureScope.invalid("凭据无效、已使用或已过期"); }
    private Profile view(User u) { return new Profile(u.getId(),u.getUsername(),u.getDisplayName(),u.getEmail(),u.getEmailVerifiedAt(),u.getRole(),u.getStatus()); }
    public PlaytestService.Page<Profile> users(Long actor,int page,int size) {
        requireAdmin(actor);String limit=FeatureScope.limit(page,size);long total=users.selectCount(null);
        return new PlaytestService.Page<>(users.selectList(new LambdaQueryWrapper<User>().orderByAsc(User::getId).last(limit)).stream().map(this::view).toList(),
            page,size,total,(total+size-1)/size);
    }
    @Transactional
    public Profile status(Long actor,Long id,UserStatus r) {
        requireAdmin(actor);if(actor.equals(id)) throw BusinessException.conflict("不能修改自己的账户状态");
        User user=users.lockById(id);if(user==null) throw BusinessException.notFound("用户不存在");
        user.setStatus(r.status());bump(user);users.updateById(user);invalidate(id);return view(user);
    }
    private void requireAdmin(Long id) {
        if(!"ADMIN".equals(active(users.selectById(id)).getRole())) throw BusinessException.forbidden("需要系统管理员权限");
    }
}
