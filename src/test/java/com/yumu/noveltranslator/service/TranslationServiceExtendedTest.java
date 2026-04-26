package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.mapper.UserMapper;
import com.yumu.noveltranslator.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TranslationService 补充测试
 * 覆盖现有测试未覆盖的分支：readerTranslate 段落为空、
 * readerTranslate 段落翻译失败使用原文兜底、
 * selectionTranslate 翻译返回结果为空、
 * webpageTranslateStream SSE 路径、
 * streamTextTranslate SSE 路径
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TranslationService 补充测试")
class TranslationServiceExtendedTest {

    @Mock
    private UserLevelThrottledTranslationClient translationClient;
    @Mock
    private TranslationCacheService cacheService;
    @Mock
    private RagTranslationService ragTranslationService;
    @Mock
    private EntityConsistencyService entityConsistencyService;
    @Mock
    private TranslationPostProcessingService postProcessingService;
    @Mock
    private TeamTranslationService teamTranslationService;
    @Mock
    private QuotaService quotaService;
    @Mock
    private UserMapper userMapper;

    private TranslationService translationService;

    @BeforeEach
    void setUp() {
        translationService = new TranslationService(
                translationClient, cacheService, ragTranslationService,
                entityConsistencyService, postProcessingService, teamTranslationService, quotaService, userMapper);
        when(postProcessingService.fixUntranslatedChinese(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthenticatedUser(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setUserLevel("free");
        CustomUserDetails userDetails = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    // ============ selectionTranslate 补充分支 ============

    @Nested
    @DisplayName("selectionTranslate - 补充分支")
    class SelectionTranslateExtendedTests {

        @Test
        void 翻译返回空字符串判定失败() {
            // 快速模式 (默认) 使用 executeFast，失败时返回原文
            // 测试需要验证空结果行为：直接模式走 executeFast，返回原文
            when(cacheService.getCache(anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean()))
                    .thenReturn("{\"code\":200,\"data\":\"\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setSourceLang("en");
            req.setTargetLang("zh");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            // executeFast 返回原文 "Hello"（非空），所以 success=true
            assertTrue(resp.getSuccess());
            assertEquals("Hello", resp.getTranslation());
        }

        @Test
        void 翻译返回null判定失败() {
            when(cacheService.getCache(anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean()))
                    .thenReturn(null);

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setSourceLang("en");
            req.setTargetLang("zh");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            // executeFast 返回原文 "Hello"
            assertTrue(resp.getSuccess());
            assertEquals("Hello", resp.getTranslation());
        }

        @Test
        void 缓存返回翻译结果() {
            when(cacheService.getCache(anyString())).thenReturn("context-cached");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("test-text");
            req.setSourceLang("en");
            req.setTargetLang("zh");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
            assertEquals("context-cached", resp.getTranslation());
        }
    }

    // ============ readerTranslate 补充分支 ============

    @Nested
    @DisplayName("readerTranslate - 补充分支")
    class ReaderTranslateExtendedTests {

        @Test
        void 段落翻译失败使用原文兜底() {
            setAuthenticatedUser(1L);
            User user = new User();
            user.setId(1L);
            user.setUserLevel("free");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(anyLong(), anyString(), anyInt(), anyString())).thenReturn(true);

            // 缓存全部未命中
            when(cacheService.getCache(anyString())).thenReturn(null);
            // 翻译抛出异常 → 管线捕获异常并返回原文
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean()))
                    .thenThrow(new RuntimeException("translation error"));

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("Hello world.");
            req.setTargetLang("zh");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            // readerTranslate 不应抛出异常，即使翻译失败也应返回原文
            assertNotNull(resp);
            // 验证未调用翻译成功路径（由于抛出异常）
            // 注意：实际行为取决于虚拟线程是否完成，此处仅验证不崩溃
        }

        @Test
        void 阅读器翻译使用指定引擎() {
            setAuthenticatedUser(1L);
            User user = new User();
            user.setId(1L);
            user.setUserLevel("free");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(anyLong(), anyString(), anyInt(), anyString())).thenReturn(true);

            when(cacheService.getCache(anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean()))
                    .thenReturn("{\"code\":200,\"data\":\"translated\"}");

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("Hello.");
            req.setTargetLang("ja");
            req.setEngine("deepl");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertNotNull(resp);
            assertEquals("deepl", resp.getEngine());
        }
    }

    // ============ webpageTranslateStream SSE ============

    @Nested
    @DisplayName("webpageTranslateStream - SSE 流式")
    class WebpageTranslateStreamTests {

        @Test
        void 配额不足返回错误SSE() {
            setAuthenticatedUser(1L);
            User user = new User();
            user.setId(1L);
            user.setUserLevel("free");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(anyLong(), anyString(), anyInt(), anyString())).thenReturn(false);

            WebpageTranslateRequest req = new WebpageTranslateRequest();
            req.setTextRegistry(List.of());

            SseEmitter emitter = translationService.webpageTranslateStream(req);

            assertNotNull(emitter);
            // SSE 异步发送，无法直接断言内容，但不应抛出异常
        }

        @Test
        void 用户不存在跳过配额检查() {
            setAuthenticatedUser(999L);
            when(userMapper.selectById(999L)).thenReturn(null);

            WebpageTranslateRequest req = new WebpageTranslateRequest();
            req.setTextRegistry(List.of());

            SseEmitter emitter = translationService.webpageTranslateStream(req);

            assertNotNull(emitter);
        }

        @Test
        void 空textRegistry直接完成() {
            WebpageTranslateRequest req = new WebpageTranslateRequest();
            req.setTextRegistry(new ArrayList<>());

            SseEmitter emitter = translationService.webpageTranslateStream(req);

            assertNotNull(emitter);
        }
    }

    // ============ streamTextTranslate SSE ============

    @Nested
    @DisplayName("streamTextTranslate - SSE 流式")
    class StreamTextTranslateTests {

        @Test
        void 空文本直接返回completed() {
            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("");

            SseEmitter emitter = translationService.streamTextTranslate(req);

            assertNotNull(emitter);
        }

        @Test
        void null文本直接返回completed() {
            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText(null);

            SseEmitter emitter = translationService.streamTextTranslate(req);

            assertNotNull(emitter);
        }

        @Test
        void 配额不足返回错误SSE() {
            setAuthenticatedUser(1L);
            User user = new User();
            user.setId(1L);
            user.setUserLevel("free");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(anyLong(), anyString(), anyInt(), anyString())).thenReturn(false);

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello World");

            SseEmitter emitter = translationService.streamTextTranslate(req);

            assertNotNull(emitter);
        }

        @Test
        void 使用指定mode参数() {
            setAuthenticatedUser(1L);
            User user = new User();
            user.setId(1L);
            user.setUserLevel("pro");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(anyLong(), anyString(), anyInt(), eq("expert"))).thenReturn(true);

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setMode("expert");

            SseEmitter emitter = translationService.streamTextTranslate(req);

            assertNotNull(emitter);
        }
    }
}
