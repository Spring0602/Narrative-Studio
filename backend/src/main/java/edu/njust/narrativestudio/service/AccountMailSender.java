package edu.njust.narrativestudio.service;
import edu.njust.narrativestudio.exception.BusinessException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
@Component
public class AccountMailSender {
    private final ObjectProvider<JavaMailSender> provider;private final String from;
    public AccountMailSender(ObjectProvider<JavaMailSender> provider,@Value("${app.mail.from:}") String from) {this.provider=provider;this.from=from;}
    public void requireConfigured() {
        if(provider.getIfAvailable()==null || from.isBlank()) throw unavailable();
    }
    public void send(String email,String purpose,String token) {
        requireConfigured();
        SimpleMailMessage message=new SimpleMailMessage();message.setFrom(from);message.setTo(email);
        message.setSubject("叙事工坊："+("VERIFY_EMAIL".equals(purpose)?"验证邮箱":"重置密码"));
        message.setText("本次操作凭据（15分钟有效，仅可使用一次）：\n"+token+"\n请在叙事工坊确认页面提交。若非本人操作，请忽略。");
        try { provider.getObject().send(message); } catch(org.springframework.mail.MailException ex) { throw unavailable(); }
    }
    private BusinessException unavailable() { return new BusinessException("MAIL_UNAVAILABLE","邮件服务尚未配置或暂不可用",HttpStatus.SERVICE_UNAVAILABLE); }
}
