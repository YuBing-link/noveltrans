package com.yumu.noveltranslator.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.annotation.PostConstruct;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 邮箱验证码工具类
 * 支持生成、发送、验证验证码
 */
@Slf4j
@Component
public class EmailVerificationCodeUtil {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${email.verification.code.validity:1}")
    private long validity; // 验证码有效期（分钟）

    @Value("${email.verification.code.length:6}")
    private int codeLength; // 验证码长度

    // 使用 Caffeine 缓存存储验证码（key: email, value: code）
    private Cache<String, String> verificationCodeCache;

    // 记录上次发送时间，限制发送频率（60秒内不能重复发送）
    private Cache<String, Long> lastSendTimeCache;

    @PostConstruct
    public void init() {
        verificationCodeCache = Caffeine.newBuilder()
                .expireAfterWrite(validity, TimeUnit.MINUTES)
                .maximumSize(10000)
                .build();
        lastSendTimeCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(10000)
                .build();
    }

    // 用于生成随机验证码
    private static final Random RANDOM = new Random();

    /**
     * 生成随机验证码
     *
     * @return 6 位数字验证码
     */
    public String generateCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < codeLength; i++) {
            code.append(RANDOM.nextInt(10));
        }
        return code.toString();
    }

    /**
     * 向指定邮箱发送验证码
     *
     * @param email 目标邮箱地址
     * @return true 发送成功，false 发送失败
     */
    public boolean sendVerificationCode(String email) {
        try {
            // 检查发送频率限制（60秒内不允许重复发送）
            Long lastSendTime = lastSendTimeCache.getIfPresent(email);
            if (lastSendTime != null) {
                long elapsed = System.currentTimeMillis() - lastSendTime;
                if (elapsed < 60000) {
                    log.warn("验证码发送过于频繁，距离上次发送仅 {} ms", elapsed);
                    return false;
                }
            }

            // 生成验证码
            String code = generateCode();

            // 先发送邮件，成功后再存储验证码
            // 避免邮件发送失败但缓存已被覆盖的问题
            sendHtmlEmail(email, code);

            // 以 email:code 为 key 存储，避免新验证码覆盖旧的
            verificationCodeCache.put(email + ":" + code, code);

            // 记录发送时间
            lastSendTimeCache.put(email, System.currentTimeMillis());

            log.info("验证码已发送至邮箱: {}", email);
            return true;
        } catch (Exception e) {
            log.error("发送验证码失败，邮箱: {}, 错误: {}", email, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 发送HTML格式的验证邮件
     */
    private void sendHtmlEmail(String email, String code) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(email);
        helper.setSubject("【小说翻译器】邮箱验证");

        // 准备邮件模板上下文
        Context context = new Context();
        context.setVariable("verificationCode", code);
        context.setVariable("validity", validity);

        // 处理邮件模板
        String htmlContent = templateEngine.process("verification-email", context);
        helper.setText(htmlContent, true); // true表示HTML格式

        mailSender.send(message);
    }

    /**
     * 验证邮箱验证码
     *
     * @param email 邮箱地址
     * @param code  用户输入的验证码
     * @return true 验证成功，false 验证失败（验证码错误或已过期）
     */
    public boolean verifyCode(String email, String code) {
        try {
            // 以 email:code 为 key 查找验证码
            String cachedCode = verificationCodeCache.getIfPresent(email + ":" + code);
            if (cachedCode == null) {
                log.warn("验证码已过期或不存在，邮箱: {}", email);
                return false;
            }

            // 验证成功，删除缓存中的验证码
            verificationCodeCache.invalidate(email + ":" + code);
            log.info("邮箱验证成功: {}", email);
            return true;
        } catch (Exception e) {
            log.error("验证验证码时出错，邮箱: {}, 错误: {}", email, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取缓存中的验证码（用于测试或调试）
     *
     * @param email 邮箱地址
     * @return 验证码，若不存在或已过期返回 null
     */
    public String getCodeFromCache(String email) {
        return verificationCodeCache.getIfPresent(email);
    }

    /**
     * 获取上次发送验证码的时间戳
     *
     * @param email 邮箱地址
     * @return 时间戳（毫秒），若未发送过返回 null
     */
    public Long getLastSendTime(String email) {
        return lastSendTimeCache.getIfPresent(email);
    }
}
