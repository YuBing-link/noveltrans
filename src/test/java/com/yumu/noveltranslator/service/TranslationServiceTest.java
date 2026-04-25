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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TranslationServiceTest {

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
    private QuotaService quotaService;

    @Mock
    private UserMapper userMapper;

    private TranslationService translationService;

    @BeforeEach
    void setUp() {
        translationService = new TranslationService(
                translationClient, cacheService, ragTranslationService,
                entityConsistencyService, postProcessingService, quotaService, userMapper);
        // Pipeline 内部调用 fixUntranslatedChinese，mock 返回原文
        when(postProcessingService.fixUntranslatedChinese(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 设置认证用户上下文
     */
    private void setAuthenticatedUser(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setUserLevel("free");
        CustomUserDetails userDetails = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    @Nested
    @DisplayName("选中文本翻译")
    class SelectionTranslateTests {

        @Test
        void 空文本返回错误() {
            SelectionTranslationRequest req = new SelectionTranslationRequest();
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertFalse(resp.getSuccess());
        }

        @Test
        void null文本返回错误() {
            SelectionTranslationRequest req = new SelectionTranslationRequest();
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertFalse(resp.getSuccess());
        }

        @Test
        void 缓存命中直接返回() {
            when(cacheService.getCache(anyString())).thenReturn("缓存结果");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("测试文本");
            req.setSourceLang("en");
            req.setTargetLang("zh");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
            assertEquals("缓存结果", resp.getTranslation());
            verify(translationClient, never()).translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean());
        }

        @Test
        void 缓存未命中调用翻译客户端() {
            when(cacheService.getCache(anyString())).thenReturn(null);
            RagTranslationResponse ragResp = new RagTranslationResponse();
            when(ragTranslationService.searchSimilar(anyString(), anyString(), anyString()))
                    .thenReturn(ragResp);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean()))
                    .thenReturn("{\"code\":200,\"data\":\"翻译结果\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello World");
            req.setSourceLang("en");
            req.setTargetLang("zh");
            req.setEngine("google");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
            assertEquals("翻译结果", resp.getTranslation());
            verify(translationClient).translate(eq("Hello World"), eq("zh"), eq("google"), eq(false), eq(false));
        }

        @Test
        void 翻译失败返回错误() {
            when(cacheService.getCache(anyString())).thenReturn(null);
            RagTranslationResponse ragResp = new RagTranslationResponse();
            when(ragTranslationService.searchSimilar(anyString(), anyString(), anyString()))
                    .thenReturn(ragResp);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean()))
                    .thenReturn("{\"code\":500,\"error\":\"引擎不可用\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello World");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            // executeFast 失败时返回原文，success=true（原文兜底）
            assertTrue(resp.getSuccess());
            assertEquals("Hello World", resp.getTranslation());
        }

        @Test
        void 默认语言和引擎() {
            when(cacheService.getCache(anyString())).thenReturn(null);
            RagTranslationResponse ragResp = new RagTranslationResponse();
            when(ragTranslationService.searchSimilar(anyString(), anyString(), anyString()))
                    .thenReturn(ragResp);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean()))
                    .thenReturn("{\"code\":200,\"data\":\"result\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("test");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
            verify(translationClient).translate(eq("test"), eq("zh"), eq("auto"), eq(false), eq(false));
        }
    }

    @Nested
    @DisplayName("阅读器翻译")
    class ReaderTranslateTests {

        @Test
        void 空内容返回错误() {
            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertFalse(resp.getSuccess());
            assertEquals("没有收到内容", resp.getTranslatedContent());
        }

        @Test
        void null内容返回错误() {
            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent(null);
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertFalse(resp.getSuccess());
        }

        @Test
        void 单段落翻译() {
            when(cacheService.getCache(anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean()))
                    .thenReturn("{\"code\":200,\"data\":\"翻译段落\"}");

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("<p>Test paragraph</p>");
            req.setTargetLang("zh");
            req.setEngine("google");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertTrue(resp.getSuccess());
            assertNotNull(resp.getTranslatedContent());
        }

        @Test
        void 多段落并行翻译() {
            when(cacheService.getCache(anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean()))
                    .thenAnswer(invocation -> {
                        String text = invocation.getArgument(0);
                        return "{\"code\":200,\"data\":\"[translated]\" + text}";
                    });

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("<p>段落1</p><p>段落2</p><p>段落3</p>");
            req.setTargetLang("zh");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertTrue(resp.getSuccess());
            assertNotNull(resp.getTranslatedContent());
        }

        @Test
        void 长文本分段翻译() {
            when(cacheService.getCache(anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean()))
                    .thenAnswer(invocation -> {
                        String text = invocation.getArgument(0);
                        return "{\"code\":200,\"data\":\"[T:\" + text.length() + \"]\"}";
                    });

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                sb.append("This is sentence number ").append(i).append(". ");
            }

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent(sb.toString());
            req.setSourceLang("en");
            req.setTargetLang("zh");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertTrue(resp.getSuccess());
            assertNotNull(resp.getTranslatedContent());
        }
    }

    @Nested
    @DisplayName("缓存统计")
    class CacheStatsTests {

        @Test
        void 获取缓存统计() {
            @SuppressWarnings("unchecked")
            var expected = (Map<String, Object>) (Map<?, ?>) Map.of("l1Hits", 5L, "misses", 2L);
            when(cacheService.getCacheStats()).thenReturn(expected);

            var stats = translationService.getCacheStats();
            assertEquals(expected, stats);
        }
    }

    @Nested
    @DisplayName("配额检查")
    class QuotaCheckTests {

        @Test
        void 字符配额不足返回错误() {
            setAuthenticatedUser(1L);
            User user = new User();
            user.setId(1L);
            user.setUserLevel("free");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(anyLong(), anyString(), anyInt(), anyString())).thenReturn(false);

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello World");
            req.setSourceLang("en");
            req.setTargetLang("zh");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertFalse(resp.getSuccess());
            assertTrue(resp.getTranslation().contains("字符配额不足"));
        }

        @Test
        void 用户不存在跳过配额检查() {
            setAuthenticatedUser(999L);
            when(userMapper.selectById(999L)).thenReturn(null);
            when(cacheService.getCache(anyString())).thenReturn("cached");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setText("Hello");
            req.setSourceLang("en");
            req.setTargetLang("zh");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
        }

        @Test
        void 阅读器翻译配额不足() {
            setAuthenticatedUser(1L);
            User user = new User();
            user.setId(1L);
            user.setUserLevel("free");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(anyLong(), anyString(), anyInt(), anyString())).thenReturn(false);

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("<p>Hello</p>");
            req.setTargetLang("zh");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertFalse(resp.getSuccess());
            assertTrue(resp.getTranslatedContent().contains("字符配额不足"));
        }

        @Test
        void 选中文本翻译异常兜底() {
            when(cacheService.getCache(anyString())).thenThrow(new RuntimeException("cache error"));

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setText("Hello");
            req.setSourceLang("en");
            req.setTargetLang("zh");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertFalse(resp.getSuccess());
            assertTrue(resp.getTranslation().contains("翻译失败"));
        }
    }
}
