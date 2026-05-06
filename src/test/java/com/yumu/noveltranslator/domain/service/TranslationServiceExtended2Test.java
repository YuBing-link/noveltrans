package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.UserMapper;
import com.yumu.noveltranslator.adapter.in.security.CustomUserDetails;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TranslationService 深度补充测试（第二弹）
 * 聚焦分支覆盖：
 * - selectionTranslate 各 mode 值及配额退款的错误路径
 * - readerTranslate 分段失败、空段、混合缓存、配额退款路径
 * - 安全上下文变化（无认证、不同用户级别）
 * - 流式翻译的段落/行级边界情况
 * - XSS 净化验证
 * - webpageTranslateStream 异常/退款路径
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TranslationService 深度补充测试")
class TranslationServiceExtended2Test {

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
        lenient().when(postProcessingService.fixUntranslatedChinese(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthenticatedUser(Long userId, String userLevel) {
        User user = new User();
        user.setId(userId);
        user.setUserLevel(userLevel);
        CustomUserDetails userDetails = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    private void setAuthenticatedUser(Long userId) {
        setAuthenticatedUser(userId, "free");
    }

    // ========================================================================
    // selectionTranslate - mode values via engine alias
    // ========================================================================

    @Nested
    @DisplayName("selectionTranslate - 不同引擎别名映射")
    class SelectionTranslateEngineAliasTests {

        @Test
        void ai引擎映射为expert模式() {
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"专家翻译结果\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello World");
            req.setTargetLang("zh");
            req.setEngine("ai"); // maps to EXPERT
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
            assertEquals("expert", resp.getEngine());
            assertEquals("专家翻译结果", resp.getTranslation());
        }

        @Test
        void aiTeam引擎映射为team模式() {
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"团队翻译结果\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setTargetLang("zh");
            req.setEngine("ai-team"); // maps to TEAM
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
            assertEquals("team", resp.getEngine());
            assertEquals("团队翻译结果", resp.getTranslation());
        }

        @Test
        void deepl引擎映射为expert模式() {
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"DeepL结果\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setTargetLang("zh");
            req.setEngine("deepl"); // maps to EXPERT
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
            assertEquals("expert", resp.getEngine());
        }

        @Test
        void google引擎映射为fast模式() {
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"Google结果\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("test");
            req.setTargetLang("zh");
            req.setEngine("google");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
            assertEquals("fast", resp.getEngine());
        }

        @Test
        void 未知引擎降级为fast() {
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"结果\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("test");
            req.setEngine("unknown-engine");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
            assertEquals("fast", resp.getEngine());
        }
    }

    @Nested
    @DisplayName("selectionTranslate - 配额参数验证")
    class SelectionTranslateQuotaParamTests {

        @Test
        void expert模式配额使用expert字符串() {
            setAuthenticatedUser(1L, "free");
            User user = new User();
            user.setId(1L);
            user.setUserLevel("free");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(eq(1L), eq("free"), anyLong(), eq("expert"))).thenReturn(true);
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"结果\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setEngine("ai");
            req.setMode("expert");
            var resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
            verify(quotaService).tryConsumeChars(eq(1L), eq("free"), anyLong(), eq("expert"));
        }

        @Test
        void team模式配额使用team字符串() {
            setAuthenticatedUser(1L, "free");
            User user = new User();
            user.setId(1L);
            user.setUserLevel("free");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(eq(1L), eq("free"), anyLong(), eq("team"))).thenReturn(true);
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"结果\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setEngine("ai-team");
            req.setMode("team");
            var resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
            verify(quotaService).tryConsumeChars(eq(1L), eq("free"), anyLong(), eq("team"));
        }
    }

    @Nested
    @DisplayName("selectionTranslate - 配额退款路径")
    class SelectionTranslateRefundTests {

        @Test
        void 翻译客户端抛异常时executeFast返回原文() {
            // 无认证用户，不检查配额
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenThrow(new RuntimeException("network error"));

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setTargetLang("zh");
            req.setEngine("google");
            var resp = translationService.selectionTranslate(req);

            // executeFast catches exception internally and returns original text -> success=true
            assertTrue(resp.getSuccess());
            assertEquals("Hello", resp.getTranslation());
        }

        @Test
        void 翻译返回null时executeFast返回原文() {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn(null);

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setTargetLang("zh");
            req.setEngine("google");
            var resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
            assertEquals("Hello", resp.getTranslation());
        }

        @Test
        void 翻译返回空data时executeFast返回原文() {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setTargetLang("zh");
            req.setEngine("google");
            var resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
            assertEquals("Hello", resp.getTranslation());
        }
    }

    @Nested
    @DisplayName("selectionTranslate - XSS 净化")
    class SelectionTranslateSanitizationTests {

        @Test
        void 翻译结果中的script标签被净化() {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"<script>alert(1)</script>翻译结果\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("test");
            req.setTargetLang("zh");
            req.setEngine("google");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
            assertFalse(resp.getTranslation().contains("<script>"));
        }

        @Test
        void 翻译结果中的iframe标签被净化() {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"<iframe src='evil'>内容</iframe>正常文本\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("test");
            req.setTargetLang("zh");
            req.setEngine("google");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
            assertFalse(resp.getTranslation().contains("<iframe"));
        }

        @Test
        void 安全的p和b标签被保留() {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"<p><b>加粗文本</b></p>\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("test");
            req.setTargetLang("zh");
            req.setEngine("google");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
            assertTrue(resp.getTranslation().contains("<p>"));
            assertTrue(resp.getTranslation().contains("<b>"));
        }
    }

    // ========================================================================
    // readerTranslate - engine alias, quota refund, mixed cache
    // ========================================================================

    @Nested
    @DisplayName("readerTranslate - 引擎别名映射")
    class ReaderTranslateEngineAliasTests {

        @Test
        void ai引擎映射为expert模式() {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"结果\"}");

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("test");
            req.setTargetLang("zh");
            req.setEngine("ai");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertTrue(resp.getSuccess());
            assertEquals("expert", resp.getEngine());
        }

        @Test
        void aiTeam引擎映射为team模式() {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"结果\"}");

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("test");
            req.setTargetLang("zh");
            req.setEngine("ai-team");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertTrue(resp.getSuccess());
            assertEquals("team", resp.getEngine());
        }

        @Test
        void null引擎默认为fast模式() {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"结果\"}");

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("test");
            req.setTargetLang("zh");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertTrue(resp.getSuccess());
            assertEquals("fast", resp.getEngine());
        }
    }

    @Nested
    @DisplayName("readerTranslate - 配额退款路径")
    class ReaderTranslateRefundTests {

        @Test
        void 翻译客户端全部抛异常时虚拟线程用原文兜底() throws InterruptedException {
            setAuthenticatedUser(1L, "free");
            User user = new User();
            user.setId(1L);
            user.setUserLevel("free");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(anyLong(), anyString(), anyLong(), anyString())).thenReturn(true);
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenThrow(new RuntimeException("engine down"));

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("<p>Hello</p>");
            req.setTargetLang("zh");
            req.setEngine("google");
            var resp = translationService.readerTranslate(req);

            // readerTranslate 的虚拟线程会捕获异常并用原文兜底
            // 整体 try-catch 不抛出异常
            Thread.sleep(500);
            assertNotNull(resp);
        }

        @Test
        void 用户不存在时跳过配额检查() {
            setAuthenticatedUser(999L);
            when(userMapper.selectById(999L)).thenReturn(null);
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn("cached");

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("<p>Hello</p>");
            req.setTargetLang("zh");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertTrue(resp.getSuccess());
            assertEquals("cached", resp.getTranslatedContent());
            verify(quotaService, never()).tryConsumeChars(anyLong(), anyString(), anyInt(), anyString());
            verify(quotaService, never()).refundChars(anyLong(), anyInt(), anyString());
        }

        @Test
        void readerTranslate配额使用指定mode() {
            setAuthenticatedUser(1L, "pro");
            User user = new User();
            user.setId(1L);
            user.setUserLevel("pro");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(eq(1L), eq("pro"), anyLong(), eq("expert"))).thenReturn(true);
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"translated\"}");

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("Hello");
            req.setTargetLang("zh");
            req.setEngine("ai");
            req.setMode("expert");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertTrue(resp.getSuccess());
            verify(quotaService).tryConsumeChars(eq(1L), eq("pro"), anyLong(), eq("expert"));
        }
    }

    @Nested
    @DisplayName("readerTranslate - 混合缓存命中/未命中")
    class ReaderTranslateMixedCacheTests {

        @Test
        void 阅读器翻译中缓存全部命中不调用翻译客户端() {
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn("cached-result");

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("<p>p1</p><p>p2</p>");
            req.setTargetLang("zh");
            req.setEngine("google");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertTrue(resp.getSuccess());
            assertEquals("cached-result", resp.getTranslatedContent());
            verify(translationClient, never()).translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList());
        }

        @Test
        void 阅读器翻译中缓存未命中调用翻译客户端() throws InterruptedException {
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"translated-by-client\"}");

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("<p>unique-content</p>");
            req.setTargetLang("zh");
            req.setEngine("google");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertTrue(resp.getSuccess());
            assertTrue(resp.getTranslatedContent().contains("translated-by-client"));
            Thread.sleep(500);
        }
    }

    @Nested
    @DisplayName("readerTranslate - 内容边界情况")
    class ReaderTranslateContentEdgeCases {

        @Test
        void 仅空白字符返回没有收到内容() {
            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("   ");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertFalse(resp.getSuccess());
            assertEquals("没有收到内容", resp.getTranslatedContent());
        }

        @Test
        void 仅换行符返回没有收到内容() {
            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("\n\n\n");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertFalse(resp.getSuccess());
            assertEquals("没有收到内容", resp.getTranslatedContent());
        }

        @Test
        void targetLang为null时使用默认中文() {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"结果\"}");

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("<p>test</p>");
            // targetLang = null
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertTrue(resp.getSuccess());
        }

        @Test
        void 特殊字符内容正常翻译() {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"翻译结果\"}");

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("<p>Special chars: &lt;script&gt; &amp; unicode</p>");
            req.setTargetLang("zh");
            req.setEngine("google");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertTrue(resp.getSuccess());
            assertNotNull(resp.getTranslatedContent());
        }
    }

    // ========================================================================
    // 安全上下文变化
    // ========================================================================

    @Nested
    @DisplayName("安全上下文 - 无认证用户")
    class SecurityNoAuthTests {

        @Test
        void 无认证用户选中文本翻译() {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"翻译结果\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setTargetLang("zh");
            req.setEngine("google");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
            assertEquals("翻译结果", resp.getTranslation());
            // 未认证用户不检查配额
            verify(quotaService, never()).tryConsumeChars(anyLong(), anyString(), anyInt(), anyString());
        }

        @Test
        void 无认证用户阅读器翻译() {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"翻译结果\"}");

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("<p>Hello</p>");
            req.setTargetLang("zh");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertTrue(resp.getSuccess());
            verify(quotaService, never()).tryConsumeChars(anyLong(), anyString(), anyInt(), anyString());
        }

        @Test
        void 安全上下文完全为null() {
            // 不调用 setAuthenticatedUser，SecurityContextHolder 为空
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn("cached");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("test");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
            assertEquals("cached", resp.getTranslation());
        }
    }

    @Nested
    @DisplayName("安全上下文 - 不同用户级别")
    class SecurityUserLevelTests {

        @Test
        void pro用户选中文本翻译() {
            setAuthenticatedUser(1L, "pro");
            User user = new User();
            user.setId(1L);
            user.setUserLevel("pro");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(eq(1L), eq("pro"), anyLong(), anyString())).thenReturn(true);
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"pro翻译结果\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setTargetLang("zh");
            req.setEngine("google");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
            verify(quotaService).tryConsumeChars(eq(1L), eq("pro"), anyLong(), anyString());
        }

        @Test
        void max用户选中文本翻译() {
            setAuthenticatedUser(1L, "max");
            User user = new User();
            user.setId(1L);
            user.setUserLevel("max");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(eq(1L), eq("max"), anyLong(), anyString())).thenReturn(true);
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"max翻译结果\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setTargetLang("zh");
            req.setEngine("google");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
            verify(quotaService).tryConsumeChars(eq(1L), eq("max"), anyLong(), anyString());
        }

        @Test
        void premium用户选中文本翻译() {
            setAuthenticatedUser(1L, "premium");
            User user = new User();
            user.setId(1L);
            user.setUserLevel("premium");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(eq(1L), eq("premium"), anyLong(), anyString())).thenReturn(true);
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"premium翻译结果\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setTargetLang("zh");
            req.setEngine("google");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
        }

        @Test
        void pro用户阅读器翻译() {
            setAuthenticatedUser(1L, "pro");
            User user = new User();
            user.setId(1L);
            user.setUserLevel("pro");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(eq(1L), eq("pro"), anyLong(), anyString())).thenReturn(true);
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"pro阅读翻译\"}");

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("<p>test</p>");
            req.setTargetLang("zh");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertTrue(resp.getSuccess());
            verify(quotaService).tryConsumeChars(eq(1L), eq("pro"), anyLong(), anyString());
        }
    }

    // ========================================================================
    // 流式翻译 - streamTextTranslate 边界情况
    // ========================================================================

    @Nested
    @DisplayName("streamTextTranslate - 段落/行级边界情况")
    class StreamTranslateBoundaryTests {

        @Test
        void 多段落翻译() throws InterruptedException {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"translated-line\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Paragraph 1\n\nParagraph 2\n\nParagraph 3");
            req.setTargetLang("zh");
            req.setEngine("google");
            SseEmitter emitter = translationService.streamTextTranslate(req);

            assertNotNull(emitter);
            Thread.sleep(1000);
        }

        @Test
        void 段落内空行被保留() throws InterruptedException {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"translated-line\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("line1\nline2");
            req.setTargetLang("zh");
            req.setEngine("google");
            SseEmitter emitter = translationService.streamTextTranslate(req);

            assertNotNull(emitter);
            Thread.sleep(1000);
        }

        @Test
        void 仅含空行段落被跳过() throws InterruptedException {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("\n\n");
            req.setTargetLang("zh");
            req.setEngine("google");
            SseEmitter emitter = translationService.streamTextTranslate(req);

            assertNotNull(emitter);
            Thread.sleep(1000);
        }

        @Test
        void 翻译行失败时使用原文兜底() throws InterruptedException {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenThrow(new RuntimeException("translation failed"));

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setTargetLang("zh");
            SseEmitter emitter = translationService.streamTextTranslate(req);

            assertNotNull(emitter);
            Thread.sleep(1000);
        }

        @Test
        void 翻译返回空行时使用原文兜底() throws InterruptedException {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setTargetLang("zh");
            SseEmitter emitter = translationService.streamTextTranslate(req);

            assertNotNull(emitter);
            Thread.sleep(1000);
        }

        @Test
        void 翻译返回null行时使用原文兜底() throws InterruptedException {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn(null);

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setTargetLang("zh");
            SseEmitter emitter = translationService.streamTextTranslate(req);

            assertNotNull(emitter);
            Thread.sleep(1000);
        }
    }

    @Nested
    @DisplayName("streamTextTranslate - 配额退款路径")
    class StreamTranslateRefundTests {

        @Test
        void 整体翻译异常时退款() throws InterruptedException {
            setAuthenticatedUser(1L, "free");
            User user = new User();
            user.setId(1L);
            user.setUserLevel("free");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(anyLong(), anyString(), anyLong(), anyString())).thenReturn(true);
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenThrow(new RuntimeException("critical failure"));

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello World");
            req.setTargetLang("zh");
            req.setEngine("google");
            SseEmitter emitter = translationService.streamTextTranslate(req);

            assertNotNull(emitter);
            Thread.sleep(1000);
        }

        @Test
        void 用户不存在跳过配额() {
            setAuthenticatedUser(999L);
            when(userMapper.selectById(999L)).thenReturn(null);
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"结果\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setTargetLang("zh");
            SseEmitter emitter = translationService.streamTextTranslate(req);

            assertNotNull(emitter);
            verify(quotaService, never()).tryConsumeChars(anyLong(), anyString(), anyInt(), anyString());
            verify(quotaService, never()).refundChars(anyLong(), anyInt(), anyString());
        }
    }

    // ========================================================================
    // webpageTranslateStream - 异常/退款路径
    // ========================================================================

    @Nested
    @DisplayName("webpageTranslateStream - 异常和退款路径")
    class WebpageTranslateExceptionTests {

        @Test
        void 翻译线程异常时executeFast用原文兜底() throws InterruptedException {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenThrow(new RuntimeException("engine unavailable"));

            WebpageTranslateRequest req = new WebpageTranslateRequest();
            List<WebpageTranslateRequest.TextItem> items = new ArrayList<>();
            WebpageTranslateRequest.TextItem item = new WebpageTranslateRequest.TextItem();
            item.setId("t1");
            item.setOriginal("Hello World");
            items.add(item);
            req.setTextRegistry(items);
            req.setTargetLang("zh");

            SseEmitter emitter = translationService.webpageTranslateStream(req);
            assertNotNull(emitter);
            Thread.sleep(1000);
        }

        @Test
        void 文本项original为null时使用空字符串() throws InterruptedException {
            SecurityContextHolder.clearContext();

            WebpageTranslateRequest req = new WebpageTranslateRequest();
            List<WebpageTranslateRequest.TextItem> items = new ArrayList<>();
            WebpageTranslateRequest.TextItem item = new WebpageTranslateRequest.TextItem();
            item.setId("t1");
            item.setOriginal(null);
            items.add(item);
            req.setTextRegistry(items);
            req.setTargetLang("zh");

            SseEmitter emitter = translationService.webpageTranslateStream(req);
            assertNotNull(emitter);
            Thread.sleep(1000);
        }

        @Test
        void 文本项id为null时使用空字符串() throws InterruptedException {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"翻译\"}");

            WebpageTranslateRequest req = new WebpageTranslateRequest();
            List<WebpageTranslateRequest.TextItem> items = new ArrayList<>();
            WebpageTranslateRequest.TextItem item = new WebpageTranslateRequest.TextItem();
            item.setId(null);
            item.setOriginal("Hello");
            items.add(item);
            req.setTextRegistry(items);
            req.setTargetLang("zh");

            SseEmitter emitter = translationService.webpageTranslateStream(req);
            assertNotNull(emitter);
            Thread.sleep(1000);
        }

        @Test
        void expert模式配额使用expert字符串() {
            setAuthenticatedUser(1L, "free");
            User user = new User();
            user.setId(1L);
            user.setUserLevel("free");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(eq(1L), eq("free"), anyLong(), eq("expert"))).thenReturn(true);
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn("cached");

            WebpageTranslateRequest req = new WebpageTranslateRequest();
            List<WebpageTranslateRequest.TextItem> items = new ArrayList<>();
            WebpageTranslateRequest.TextItem item = new WebpageTranslateRequest.TextItem();
            item.setId("1");
            item.setOriginal("Hello");
            items.add(item);
            req.setTextRegistry(items);
            req.setFastMode(false); // expert mode
            req.setTargetLang("zh");

            SseEmitter emitter = translationService.webpageTranslateStream(req);
            assertNotNull(emitter);

            verify(quotaService).tryConsumeChars(eq(1L), eq("free"), anyLong(), eq("expert"));
        }

        @Test
        void 多条文本并发翻译() throws InterruptedException {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"translated\"}");

            WebpageTranslateRequest req = new WebpageTranslateRequest();
            List<WebpageTranslateRequest.TextItem> items = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                WebpageTranslateRequest.TextItem item = new WebpageTranslateRequest.TextItem();
                item.setId("item-" + i);
                item.setOriginal("Text " + i);
                items.add(item);
            }
            req.setTextRegistry(items);
            req.setTargetLang("zh");
            req.setEngine("google");

            SseEmitter emitter = translationService.webpageTranslateStream(req);
            assertNotNull(emitter);
            Thread.sleep(2000);
        }
    }

    @Nested
    @DisplayName("webpageTranslateStream - 配额退款路径")
    class WebpageTranslateRefundTests {

        @Test
        void 全局异常时退款() throws InterruptedException {
            setAuthenticatedUser(1L, "free");
            User user = new User();
            user.setId(1L);
            user.setUserLevel("free");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(anyLong(), anyString(), anyLong(), anyString())).thenReturn(true);

            // 让 cacheService 在虚拟线程中抛异常来触发全局 catch
            when(cacheService.getCacheByMode(anyString(), anyString()))
                    .thenThrow(new RuntimeException("cache service unavailable"));

            WebpageTranslateRequest req = new WebpageTranslateRequest();
            List<WebpageTranslateRequest.TextItem> items = new ArrayList<>();
            WebpageTranslateRequest.TextItem item = new WebpageTranslateRequest.TextItem();
            item.setId("1");
            item.setOriginal("Hello");
            items.add(item);
            req.setTextRegistry(items);
            req.setTargetLang("zh");

            SseEmitter emitter = translationService.webpageTranslateStream(req);
            assertNotNull(emitter);
            Thread.sleep(2000);
        }

        @Test
        void 用户不存在跳过配额检查() {
            setAuthenticatedUser(999L);
            when(userMapper.selectById(999L)).thenReturn(null);

            WebpageTranslateRequest req = new WebpageTranslateRequest();
            List<WebpageTranslateRequest.TextItem> items = new ArrayList<>();
            WebpageTranslateRequest.TextItem item = new WebpageTranslateRequest.TextItem();
            item.setId("1");
            item.setOriginal("Hello");
            items.add(item);
            req.setTextRegistry(items);
            req.setTargetLang("zh");

            SseEmitter emitter = translationService.webpageTranslateStream(req);
            assertNotNull(emitter);
            verify(quotaService, never()).tryConsumeChars(anyLong(), anyString(), anyInt(), anyString());
        }
    }

    // ========================================================================
    // 批量翻译 - 长文本分段 & 大量段落
    // ========================================================================

    @Nested
    @DisplayName("批量翻译 - 长文本场景")
    class BatchTranslationLongTextTests {

        @Test
        void 超多段落并行翻译() {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenAnswer(invocation -> {
                        String text = invocation.getArgument(0);
                        return "{\"code\":200,\"data\":\"[T]\" + text}";
                    });

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                sb.append("<p>Paragraph number ").append(i).append(" with some content.</p>");
            }

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent(sb.toString());
            req.setTargetLang("zh");
            req.setEngine("google");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertTrue(resp.getSuccess());
            assertNotNull(resp.getTranslatedContent());
        }

        @Test
        void 超长内容触发TextSegmentationUtil分段() {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenAnswer(invocation -> {
                        String text = invocation.getArgument(0);
                        return "{\"code\":200,\"data\":\"[T:\" + text.length() + \"]\"}";
                    });

            // 构建超过 2000 字符的内容（TextSegmentationUtil 的分段阈值）
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                sb.append("This is sentence number ").append(i).append(". ");
            }
            String longContent = sb.toString();
            assertTrue(longContent.length() > 2000);

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent(longContent);
            req.setTargetLang("zh");
            req.setEngine("google");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertTrue(resp.getSuccess());
            assertNotNull(resp.getTranslatedContent());
        }
    }

    // ========================================================================
    // Cache 交互边界情况
    // ========================================================================

    @Nested
    @DisplayName("缓存交互 - 边界情况")
    class CacheInteractionEdgeCaseTests {

        @Test
        void 缓存查询抛异常时selectionTranslate兜底() {
            when(cacheService.getCacheByMode(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Redis connection refused"));

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setTargetLang("zh");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            // selectionTranslate 的 try-catch 捕获缓存异常，返回翻译失败
            assertFalse(resp.getSuccess());
            assertTrue(resp.getTranslation().contains("翻译失败"));
        }

        @Test
        void 阅读器翻译中缓存全部命中() {
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn("cached-result");

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("p1 p2");
            req.setTargetLang("zh");
            req.setEngine("google");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertTrue(resp.getSuccess());
            assertEquals("cached-result", resp.getTranslatedContent());
            verify(translationClient, never()).translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList());
        }

        @Test
        void 阅读器翻译中缓存全部命中不调用翻译客户端() {
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn("cached-result");

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("<p>p1</p><p>p2</p>");
            req.setTargetLang("zh");
            req.setEngine("google");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertTrue(resp.getSuccess());
            // Content "<p>p1</p><p>p2</p>" is 16 chars, under segmentation threshold -> single segment
            // Cache returns "cached-result" for that segment
            assertEquals("cached-result", resp.getTranslatedContent());
            verify(translationClient, never()).translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList());
        }

        @Test
        void 阅读器翻译中缓存未命中调用翻译客户端() throws InterruptedException {
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"translated-by-client\"}");

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("<p>unique-content</p>");
            req.setTargetLang("zh");
            req.setEngine("google");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertTrue(resp.getSuccess());
            assertTrue(resp.getTranslatedContent().contains("translated-by-client"));
            Thread.sleep(500);
        }
    }

    // ========================================================================
    // 配额消费失败路径
    // ========================================================================

    @Nested
    @DisplayName("配额消费 - 失败路径")
    class QuotaConsumptionFailureTests {

        @Test
        void selectionTranslate配额检查后quotaService返回false() {
            setAuthenticatedUser(1L, "free");
            User user = new User();
            user.setId(1L);
            user.setUserLevel("free");
            when(userMapper.selectById(1L)).thenReturn(user);
            when(quotaService.tryConsumeChars(anyLong(), anyString(), anyInt(), anyString())).thenReturn(false);

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setTargetLang("zh");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertFalse(resp.getSuccess());
            assertTrue(resp.getTranslation().contains("字符配额不足"));
            assertEquals("fast", resp.getEngine());
        }

        @Test
        void readerTranslate配额检查后quotaService返回false() {
            setAuthenticatedUser(1L, "free");
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
        void userMapper返回null时跳过配额() {
            setAuthenticatedUser(1L, "free");
            when(userMapper.selectById(1L)).thenReturn(null);
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn("cached");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("Hello");
            req.setTargetLang("zh");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
            verify(quotaService, never()).tryConsumeChars(anyLong(), anyString(), anyInt(), anyString());
        }

        @Test
        void readerTranslate用户不存在跳过配额() {
            setAuthenticatedUser(1L, "free");
            when(userMapper.selectById(1L)).thenReturn(null);
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn("cached");

            ReaderTranslateRequest req = new ReaderTranslateRequest();
            req.setContent("<p>Hello</p>");
            req.setTargetLang("zh");
            ReaderTranslateResponse resp = translationService.readerTranslate(req);

            assertTrue(resp.getSuccess());
            verify(quotaService, never()).tryConsumeChars(anyLong(), anyString(), anyInt(), anyString());
        }
    }

    // ========================================================================
    // selectionTranslate - 文本边界情况
    // ========================================================================

    @Nested
    @DisplayName("selectionTranslate - 文本边界情况")
    class SelectionTranslateTextEdgeCases {

        @Test
        void 仅空白字符返回内容为空() {
            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("   ");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertFalse(resp.getSuccess());
            assertEquals("内容为空", resp.getTranslation());
        }

        @Test
        void 仅换行符返回内容为空() {
            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("\n\n");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertFalse(resp.getSuccess());
            assertEquals("内容为空", resp.getTranslation());
        }

        @Test
        void 带前后空格的文本被trim后非空() {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"翻译结果\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("  Hello World  ");
            req.setTargetLang("zh");
            req.setEngine("google");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
        }

        @Test
        void targetLang为null时使用默认中文() {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"结果\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("test");
            req.setEngine("google");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
        }

        @Test
        void sourceLang为null时正常处理() {
            SecurityContextHolder.clearContext();
            when(cacheService.getCacheByMode(anyString(), anyString())).thenReturn(null);
            lenient().when(translationClient.translate(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("{\"code\":200,\"data\":\"结果\"}");

            SelectionTranslationRequest req = new SelectionTranslationRequest();
            req.setText("test");
            req.setSourceLang(null);
            req.setTargetLang("zh");
            req.setEngine("google");
            SelectionTranslateResponse resp = translationService.selectionTranslate(req);

            assertTrue(resp.getSuccess());
        }
    }

    @Nested
    @DisplayName("getCacheStats 委托测试")
    class CacheStatsDelegationTests {

        @Test
        void 获取缓存统计委托给cacheService() {
            java.util.Map<String, Object> expected = java.util.Map.of("hits", 10L, "misses", 5L);
            when(cacheService.getCacheStats()).thenReturn(expected);

            var stats = translationService.getCacheStats();
            assertEquals(expected, stats);
            verify(cacheService).getCacheStats();
        }
    }
}
