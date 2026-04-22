package com.yumu.noveltranslator.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.internet.MimeMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailVerificationCodeUtilTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private MimeMessage mimeMessage;

    private EmailVerificationCodeUtil util;

    @BeforeEach
    void setUp() {
        util = new EmailVerificationCodeUtil();
        ReflectionTestUtils.setField(util, "mailSender", mailSender);
        ReflectionTestUtils.setField(util, "templateEngine", templateEngine);
        ReflectionTestUtils.setField(util, "fromEmail", "test@example.com");
        ReflectionTestUtils.setField(util, "validity", 1L);
        ReflectionTestUtils.setField(util, "codeLength", 6);
        util.init();
    }

    @Nested
    @DisplayName("生成验证码")
    class GenerateCodeTests {

        @Test
        void 生成6位纯数字验证码() {
            String code = util.generateCode();

            assertNotNull(code);
            assertEquals(6, code.length());
            assertTrue(code.matches("\\d{6}"));
        }

        @Test
        void 多次生成均为6位数字() {
            String code1 = util.generateCode();
            String code2 = util.generateCode();
            assertEquals(6, code1.length());
            assertEquals(6, code2.length());
            assertTrue(code1.matches("\\d{6}"));
            assertTrue(code2.matches("\\d{6}"));
        }
    }

    @Nested
    @DisplayName("发送验证码")
    class SendVerificationCodeTests {

        @Test
        void 首次发送成功() throws Exception {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            when(templateEngine.process(anyString(), any())).thenReturn("<html>验证码: 123456</html>");
            doNothing().when(mailSender).send(any(MimeMessage.class));

            boolean result = util.sendVerificationCode("user@example.com");

            assertTrue(result);
            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        void 六十秒内重复发送返回false() throws Exception {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            when(templateEngine.process(anyString(), any())).thenReturn("<html>验证码: 123456</html>");
            doNothing().when(mailSender).send(any(MimeMessage.class));

            // 第一次发送
            boolean first = util.sendVerificationCode("user@example.com");
            assertTrue(first);

            // 立即第二次发送，应被频率限制拦截
            boolean second = util.sendVerificationCode("user@example.com");
            assertFalse(second);

            // 邮件只发送了一次
            verify(mailSender, times(1)).send(any(MimeMessage.class));
        }

        @Test
        void 邮件发送失败返回false() throws Exception {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            when(templateEngine.process(anyString(), any())).thenThrow(new RuntimeException("template error"));

            boolean result = util.sendVerificationCode("user@example.com");

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("验证码校验")
    class VerifyCodeTests {

        /**
         * 从模板渲染内容中提取验证码
         */
        private String captureCodeFromTemplate() {
            ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
            when(templateEngine.process(anyString(), contextCaptor.capture())).thenAnswer(invocation -> {
                Context ctx = contextCaptor.getValue();
                return "<html>" + ctx.getVariable("verificationCode") + "</html>";
            });
            // Trigger the template processing
            doAnswer(invocation -> null).when(mailSender).send(any(MimeMessage.class));

            util.sendVerificationCode("user@example.com");

            Context ctx = contextCaptor.getValue();
            return (String) ctx.getVariable("verificationCode");
        }

        @Test
        void 正确验证码返回true并消费() throws Exception {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
            doNothing().when(mailSender).send(any(MimeMessage.class));

            // Capture the code from the template context
            ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
            when(templateEngine.process(anyString(), contextCaptor.capture())).thenReturn("<html>code</html>");

            util.sendVerificationCode("user@example.com");

            // Get the code that was set in the context
            Context capturedContext = contextCaptor.getValue();
            String code = (String) capturedContext.getVariable("verificationCode");
            assertNotNull(code);
            assertEquals(6, code.length());

            // 验证正确验证码
            boolean result = util.verifyCode("user@example.com", code);
            assertTrue(result);

            // 验证码已被消费，再次验证应失败
            boolean result2 = util.verifyCode("user@example.com", code);
            assertFalse(result2);
        }

        @Test
        void 错误验证码返回false() {
            boolean result = util.verifyCode("user@example.com", "999999");
            assertFalse(result);
        }

        @Test
        void 不存在的验证码返回false() {
            boolean result = util.verifyCode("nonexistent@example.com", "123456");
            assertFalse(result);
        }
    }
}
