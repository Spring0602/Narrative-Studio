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
    // 【解释】邮件组件与发件地址必须已配置，未配置时业务给出明确不可用提示。
    public void requireConfigured() {
        if(provider.getIfAvailable()==null || from.isBlank()) throw unavailable();
    }
    // 【解释】根据用途选择邮件标题，只把凭据发到目标邮箱；不在 HTTP 或日志中返回凭据。
    // 【常见修改】标题/正文可改；有效期文字必须与 AccountService.issue 的 plusMinutes 一致。
    public void send(String email,String purpose,String token) {
        requireConfigured();
        SimpleMailMessage message=new SimpleMailMessage();message.setFrom(from);message.setTo(email);
        message.setSubject("叙事工坊："+("VERIFY_EMAIL".equals(purpose)?"验证邮箱":"重置密码"));
        message.setText("本次操作凭据（15分钟有效，仅可使用一次）：\n"+token+"\n请在叙事工坊确认页面提交。若非本人操作，请忽略。");
        try { provider.getObject().send(message); } catch(org.springframework.mail.MailException ex) { throw unavailable(); }
    }
    // 【解释】统一邮件不可用异常，不向用户暴露 SMTP 密码或底层异常详情。
    private BusinessException unavailable() { return new BusinessException("MAIL_UNAVAILABLE","邮件服务尚未配置或暂不可用",HttpStatus.SERVICE_UNAVAILABLE); }
}
