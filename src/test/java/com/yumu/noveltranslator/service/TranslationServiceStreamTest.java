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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TranslationService 流式翻译测试")
class TranslationServiceStreamTest {

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

    private TranslationService service;

    @BeforeEach
    void setUp() {
        service = new TranslationService(
                translationClient, cacheService, ragTranslationService,
                entityConsistencyService, postProcessingService, quotaService, userMapper);
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

    // ============ streamTextTranslate 测试 ============

    @Nested
    @DisplayName("streamTextTranslate - 文本流式翻译")
    class StreamTextTranslateTests {

        @Test
        void 空文本立即返回错误() {
            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("");
            SseEmitter emitter = service.streamTextTranslate(req);
            assertNotNull(emitter);
        }

        @Test
        void null文本立即返回错误() {
            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText(null);
            SseEmitter emitter = service.streamTextTranslate(req);
            assertNotNull(emitter);
        }

        @Test
        void 配额不足立即返回错误() {
            setAuthenticatedUser(1L);
            User user = new User();
            user.setId(1L);
            user.setUserLevel("free");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(anyLong(), anyString(), anyInt(), anyString())).thenReturn(false);

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello World");
            req.setEngine("google");
            req.setTargetLang("zh");
            SseEmitter emitter = service.streamTextTranslate(req);
            assertNotNull(emitter);
        }

        @Test
        void 无认证用户正常翻译() {
            SecurityContextHolder.clearContext();
            when(cacheService.getCache(anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn("{\"code\":200,\"data\":\"翻译结果\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setEngine("google");
            req.setTargetLang("zh");
            SseEmitter emitter = service.streamTextTranslate(req);
            assertNotNull(emitter);
        }
    }

    // ============ webpageTranslateStream 测试 ============

    @Nested
    @DisplayName("webpageTranslateStream - 网页流式翻译")
    class WebpageTranslateStreamTests {

        @Test
        void 返回SseEmitter() {
            WebpageTranslateRequest req = new WebpageTranslateRequest();
            req.setTextRegistry(List.of());
            req.setTargetLang("zh");
            SseEmitter emitter = service.webpageTranslateStream(req);
            assertNotNull(emitter);
        }

        @Test
        void 空items正常返回done() {
            WebpageTranslateRequest req = new WebpageTranslateRequest();
            req.setTextRegistry(List.of());
            req.setTargetLang("zh");
            req.setEngine("google");
            SseEmitter emitter = service.webpageTranslateStream(req);
            assertNotNull(emitter);
        }

        @Test
        void 配额不足直接发送错误() {
            setAuthenticatedUser(1L);
            User user = new User();
            user.setId(1L);
            user.setUserLevel("free");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(anyLong(), anyString(), anyInt(), anyString())).thenReturn(false);

            WebpageTranslateRequest req = new WebpageTranslateRequest();
            req.setTextRegistry(List.of(createTextItem("1", "Hello")));
            SseEmitter emitter = service.webpageTranslateStream(req);
            assertNotNull(emitter);
        }

        @Test
        void 单条文本翻译成功() throws InterruptedException {
            setAuthenticatedUser(1L);
            User user = new User();
            user.setId(1L);
            user.setUserLevel("free");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(anyLong(), anyString(), anyLong(), anyString())).thenReturn(true);
            when(cacheService.getCache(anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn("{\"code\":200,\"data\":\"你好\"}");

            WebpageTranslateRequest req = new WebpageTranslateRequest();
            req.setTextRegistry(List.of(createTextItem("1", "Hello World")));
            req.setTargetLang("zh");
            req.setEngine("google");
            SseEmitter emitter = service.webpageTranslateStream(req);
            assertNotNull(emitter);

            // Allow virtual threads to complete
            Thread.sleep(2000);
        }

        @Test
        void 翻译失败使用原文兜底() {
            WebpageTranslateRequest req = new WebpageTranslateRequest();
            List<WebpageTranslateRequest.TextItem> items = new ArrayList<>();
            items.add(createTextItem("1", "Hello"));
            req.setTextRegistry(items);
            req.setTargetLang("zh");

            // No auth user, no quota check
            SecurityContextHolder.clearContext();
            when(cacheService.getCache(anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn(null); // simulate failure

            SseEmitter emitter = service.webpageTranslateStream(req);
            assertNotNull(emitter);
        }

        @Test
        void fastModeFalse使用专家模式() {
            WebpageTranslateRequest req = new WebpageTranslateRequest();
            req.setTextRegistry(List.of(createTextItem("1", "Test")));
            req.setTargetLang("zh");
            req.setFastMode(false);
            req.setEngine("google");

            SecurityContextHolder.clearContext();
            when(cacheService.getCache(anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn("{\"code\":200,\"data\":\"Expert translation\"}");

            SseEmitter emitter = service.webpageTranslateStream(req);
            assertNotNull(emitter);
        }

        private WebpageTranslateRequest.TextItem createTextItem(String id, String original) {
            WebpageTranslateRequest.TextItem item = new WebpageTranslateRequest.TextItem();
            item.setId(id);
            item.setOriginal(original);
            return item;
        }
    }

    // ============ 配额与模式测试 ============

    @Nested
    @DisplayName("配额模式 - fast/expert")
    class QuotaModeTests {

        @Test
        void fastMode使用fast配额() {
            setAuthenticatedUser(1L);
            User user = new User();
            user.setId(1L);
            user.setUserLevel("pro");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(anyLong(), anyString(), anyLong(), anyString())).thenReturn(true);
            when(cacheService.getCache(anyString())).thenReturn("cached");

            // fastMode = true -> mode = "fast"
            WebpageTranslateRequest req = new WebpageTranslateRequest();
            req.setTextRegistry(List.of(createTextItem("1", "Hello")));
            req.setFastMode(true);
            req.setTargetLang("zh");
            SseEmitter emitter = service.webpageTranslateStream(req);
            assertNotNull(emitter);

            verify(quotaService).tryConsumeChars(eq(1L), eq("pro"), anyLong(), eq("fast"));
        }

        @Test
        void expertMode使用expert配额() {
            setAuthenticatedUser(1L);
            User user = new User();
            user.setId(1L);
            user.setUserLevel("pro");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(anyLong(), anyString(), anyLong(), anyString())).thenReturn(true);
            when(cacheService.getCache(anyString())).thenReturn("cached");

            WebpageTranslateRequest req = new WebpageTranslateRequest();
            req.setTextRegistry(List.of(createTextItem("1", "Hello")));
            req.setFastMode(false);
            req.setTargetLang("zh");
            SseEmitter emitter = service.webpageTranslateStream(req);
            assertNotNull(emitter);

            verify(quotaService).tryConsumeChars(eq(1L), eq("pro"), anyLong(), eq("expert"));
        }

        private WebpageTranslateRequest.TextItem createTextItem(String id, String original) {
            WebpageTranslateRequest.TextItem item = new WebpageTranslateRequest.TextItem();
            item.setId(id);
            item.setOriginal(original);
            return item;
        }
    }

    // ============ selectionTranslate mode参数测试 ============

    @Nested
    @DisplayName("selectionTranslate - mode参数")
    class SelectionModeTests {

        @Test
        void 默认fast模式() {
            setAuthenticatedUser(1L);
            User user = new User();
            user.setId(1L);
            user.setUserLevel("free");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(anyLong(), anyString(), anyLong(), anyString())).thenReturn(true);
            when(cacheService.getCache(anyString())).thenReturn(null);
            when(ragTranslationService.searchSimilar(anyString(), anyString(), anyString()))
                    .thenReturn(new RagTranslationResponse());
            when(entityConsistencyService.shouldUseConsistency(anyString())).thenReturn(false);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn("{\"code\":200,\"data\":\"结果\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setContext("Hello");
            req.setTargetLang("zh");
            // No mode set, should default to "fast"
            var resp = service.selectionTranslate(req);
            assertTrue(resp.getSuccess());

            verify(quotaService).tryConsumeChars(eq(1L), eq("free"), anyLong(), eq("fast"));
        }

        @Test
        void expert模式() {
            setAuthenticatedUser(1L);
            User user = new User();
            user.setId(1L);
            user.setUserLevel("free");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(anyLong(), anyString(), anyLong(), anyString())).thenReturn(true);
            when(cacheService.getCache(anyString())).thenReturn(null);
            when(ragTranslationService.searchSimilar(anyString(), anyString(), anyString()))
                    .thenReturn(new RagTranslationResponse());
            when(entityConsistencyService.shouldUseConsistency(anyString())).thenReturn(false);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn("{\"code\":200,\"data\":\"结果\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setContext("Hello");
            req.setTargetLang("zh");
            req.setMode("expert");
            var resp = service.selectionTranslate(req);
            assertTrue(resp.getSuccess());

            verify(quotaService).tryConsumeChars(eq(1L), eq("free"), anyLong(), eq("expert"));
        }

        @Test
        void 翻译返回空结果() {
            when(cacheService.getCache(anyString())).thenReturn(null);
            when(ragTranslationService.searchSimilar(anyString(), anyString(), anyString()))
                    .thenReturn(new RagTranslationResponse());
            when(entityConsistencyService.shouldUseConsistency(anyString())).thenReturn(false);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenReturn("");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setContext("Hello");
            req.setTargetLang("zh");
            var resp = service.selectionTranslate(req);

            assertFalse(resp.getSuccess());
            assertTrue(resp.getTranslation().contains("结果为空"));
        }

        @Test
        void 翻译客户端抛异常() {
            when(cacheService.getCache(anyString())).thenReturn(null);
            when(ragTranslationService.searchSimilar(anyString(), anyString(), anyString()))
                    .thenReturn(new RagTranslationResponse());
            when(entityConsistencyService.shouldUseConsistency(anyString())).thenReturn(false);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean()))
                    .thenThrow(new RuntimeException("connection timeout"));

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setContext("Hello");
            req.setTargetLang("zh");
            var resp = service.selectionTranslate(req);

            assertFalse(resp.getSuccess());
            assertTrue(resp.getTranslation().contains("connection timeout"));
        }
    }

    // ============ readerTranslate 补充测试 ============

    @Nested
    @DisplayName("readerTranslate - 补充测试")
    class ReaderTranslateExtendedTests {

        @Test
        void 缓存全部命中不调用翻译客户端() {
            when(cacheService.getCache(anyString())).thenReturn("cached translation");

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("<p>test</p>");
            req.setTargetLang("zh");
            req.setEngine("google");
            var resp = service.readerTranslate(req);

            assertTrue(resp.getSuccess());
            assertEquals("cached translation", resp.getTranslatedContent());
            verify(translationClient, never()).translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean());
        }

        @Test
        void 翻译失败时使用原文兜底() {
            when(cacheService.getCache(anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean()))
                    .thenThrow(new RuntimeException("engine failed"));

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("<p>Hello</p>");
            req.setTargetLang("zh");
            req.setEngine("google");
            var resp = service.readerTranslate(req);

            assertTrue(resp.getSuccess());
            // Should fall back to original text
            assertNotNull(resp.getTranslatedContent());
        }

        @Test
        void 阅读器模式html为true() {
            when(cacheService.getCache(anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean()))
                    .thenReturn("{\"code\":200,\"data\":\"翻译结果\"}");

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("<p>test</p>");
            req.setTargetLang("zh");
            var resp = service.readerTranslate(req);

            assertTrue(resp.getSuccess());
            // reader mode uses html=true for MTranServer
            verify(translationClient).translate(anyString(), anyString(), anyString(), eq(false), eq(true));
        }
    }
}
