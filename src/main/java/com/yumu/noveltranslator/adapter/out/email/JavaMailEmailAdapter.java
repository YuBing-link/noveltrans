package com.yumu.noveltranslator.adapter.out.email;

import com.yumu.noveltranslator.port.out.EmailPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * EmailPort adapter using JavaMailSender + Thymeleaf templates.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JavaMailEmailAdapter implements EmailPort {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${email.verification.code.validity:1}")
    private long validityMinutes;

    @Override
    public void sendVerificationCode(String to, String code) {
        sendTemplatedEmail(to, "【小说翻译器】邮箱验证", "verification-email", code);
    }

    @Override
    public void sendPasswordResetCode(String to, String code) {
        sendTemplatedEmail(to, "【小说翻译器】密码重置", "verification-email", code);
    }

    private void sendTemplatedEmail(String to, String subject, String templateName, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);

            Context context = new Context();
            context.setVariable("verificationCode", code);
            context.setVariable("validity", validityMinutes);
            String htmlContent = templateEngine.process(templateName, context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("邮件已发送至: {}", to);
        } catch (MessagingException e) {
            log.error("发送邮件失败，收件人: {}, 错误: {}", to, e.getMessage(), e);
            throw new RuntimeException("邮件发送失败", e);
        }
    }
}
