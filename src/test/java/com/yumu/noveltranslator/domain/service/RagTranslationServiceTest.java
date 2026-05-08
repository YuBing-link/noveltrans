package com.yumu.noveltranslator.domain.service;
import com.yumu.noveltranslator.domain.service.TranslationMemoryService;
import com.yumu.noveltranslator.application.service.RagTranslationApplicationService;

import com.yumu.noveltranslator.port.dto.translation.RagTranslationResponse;
import com.yumu.noveltranslator.port.out.EmbeddingPort;
import com.yumu.noveltranslator.adapter.out.persistence.entity.TranslationMemory;
import com.yumu.noveltranslator.adapter.out.security.CustomUserDetails;
import com.yumu.noveltranslator.domain.model.User;
import com.yumu.noveltranslator.port.out.VectorSearchResult;
import com.yumu.noveltranslator.port.out.VectorStorePort;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RagTranslationServiceTest {

    @Mock
    private EmbeddingPort embeddingPort;

    @Mock
    private TranslationMemoryService translationMemoryService;

    @Mock
    private VectorStorePort vectorStorePort;

    private RagTranslationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new RagTranslationApplicationService(embeddingPort, translationMemoryService, vectorStorePort);
        ReflectionTestUtils.setField(service, "directHitThreshold", 0.85);
        ReflectionTestUtils.setField(service, "referenceThreshold", 0.5);
        ReflectionTestUtils.setField(service, "knnTopK", 5);
        ReflectionTestUtils.setField(service, "fallbackMysqlLimit", 20);
        ReflectionTestUtils.setField(service, "provider", "openai");
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
        CustomUserDetails userDetails = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    /**
     * 构建 VectorSearchResult 返回结果
     */
    private List<VectorSearchResult> buildVectorSearchResult(long total, String sourceText, String targetText, double similarity, Long id) {
        List<VectorSearchResult> result = new java.util.ArrayList<>();
        if (total > 0) {
            result.add(new VectorSearchResult(id, sourceText, targetText, similarity));
        }
        return result;
    }

    /**
     * 模拟 vectorStorePort.vectorSearch 调用
     */
    private void mockVectorSearch(List<VectorSearchResult> result) {
        when(vectorStorePort.vectorSearch(any(float[].class), anyLong(), anyString(), anyList(), anyInt()))
                .thenReturn(result);
    }

    @Nested
    @DisplayName("searchSimilar - 边界条件")
    class SearchSimilarBoundaryTests {

        @Test
        void 空文本返回空响应() {
            setAuthenticatedUser(1L);
            RagTranslationResponse response = service.searchSimilarWithModes(1L, null, "zh", List.of("google"));
            assertFalse(response.isDirectHit());
            assertTrue(response.getMatches().isEmpty());
        }

        @Test
        void 空白文本返回空响应() {
            setAuthenticatedUser(1L);
            RagTranslationResponse response = service.searchSimilarWithModes(1L, "   ", "zh", List.of("google"));
            assertFalse(response.isDirectHit());
            assertTrue(response.getMatches().isEmpty());
        }

        @Test
        void 未认证用户返回空响应() {
            SecurityContextHolder.clearContext();
            RagTranslationResponse response = service.searchSimilarWithModes(null, "Hello world", "zh", List.of("google"));
            assertFalse(response.isDirectHit());
            assertTrue(response.getMatches().isEmpty());
        }

        @Test
        void 向量为空返回空响应() {
            setAuthenticatedUser(1L);
            when(embeddingPort.embed(anyString())).thenReturn(new float[0]);

            RagTranslationResponse response = service.searchSimilarWithModes(1L, "Hello world", "zh", List.of("google"));
            assertFalse(response.isDirectHit());
            assertTrue(response.getMatches().isEmpty());
        }

        @Test
        void 异常处理返回空响应() {
            setAuthenticatedUser(1L);
            when(embeddingPort.embed(anyString())).thenThrow(new RuntimeException("embedding failed"));

            RagTranslationResponse response = service.searchSimilarWithModes(1L, "Hello world", "zh", List.of("google"));
            assertFalse(response.isDirectHit());
            assertTrue(response.getMatches().isEmpty());
        }
    }

    @Nested
    @DisplayName("searchSimilar - Redis 命中")
    class SearchSimilarRedisHitTests {

        @Test
        void 直接命中相似度超过阈值() {
            setAuthenticatedUser(1L);
            float[] vec = new float[]{0.1f, 0.2f, 0.3f};
            when(embeddingPort.embed("Hello world")).thenReturn(vec);

            // similarity = 0.9 >= 0.85 direct hit
            List<VectorSearchResult> searchResult = buildVectorSearchResult(1,
                    "Hello world", "你好世界", 0.9, 1L);
            mockVectorSearch(searchResult);

            RagTranslationResponse response = service.searchSimilarWithModes(1L, "Hello world", "zh", List.of("google"));

            assertTrue(response.isDirectHit());
            assertEquals("你好世界", response.getTranslation());
            assertEquals(0.9, response.getSimilarity(), 0.01);
            assertFalse(response.getMatches().isEmpty());
            verify(translationMemoryService).incrementUsage(1L);
        }

        @Test
        void 参考匹配相似度在参考阈值以上但未达直接命中() {
            setAuthenticatedUser(1L);
            float[] vec = new float[]{0.1f, 0.2f, 0.3f};
            when(embeddingPort.embed("Hello world")).thenReturn(vec);

            // similarity = 0.6, 0.5 <= 0.6 < 0.85
            List<VectorSearchResult> searchResult = buildVectorSearchResult(1,
                    "Hello world", "你好世界", 0.6, 1L);
            mockVectorSearch(searchResult);

            RagTranslationResponse response = service.searchSimilarWithModes(1L, "Hello world", "zh", List.of("google"));

            assertFalse(response.isDirectHit());
            assertNull(response.getTranslation());
            assertEquals(0.6, response.getSimilarity(), 0.01);
            assertFalse(response.getMatches().isEmpty());
            verify(translationMemoryService, never()).incrementUsage(anyLong());
        }
    }

    @Nested
    @DisplayName("searchSimilar - 无匹配")
    class SearchSimilarNoMatchTests {

        @Test
        void Redis和MySQL均无匹配返回空响应() {
            setAuthenticatedUser(1L);
            float[] vec = new float[]{0.1f, 0.2f, 0.3f};
            when(embeddingPort.embed("Hello world")).thenReturn(vec);
            mockVectorSearch(Collections.emptyList());
            when(translationMemoryService.searchByUserAndLang(anyLong(), anyString(), anyString(), anyInt()))
                    .thenReturn(Collections.emptyList());

            RagTranslationResponse response = service.searchSimilarWithModes(1L, "Hello world", "zh", List.of("google"));

            assertFalse(response.isDirectHit());
            assertTrue(response.getMatches().isEmpty());
        }
    }

    @Nested
    @DisplayName("storeTranslationMemory - 质量筛选")
    class StoreTranslationMemoryQualityTests {

        @Test
        void 未认证用户不存储() {
            SecurityContextHolder.clearContext();
            service.storeTranslationMemory("Hello", "你好", "zh", "google", null, null);
            verify(translationMemoryService, never()).storeTranslation(anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString());
        }

        @Test
        void 译文为空被拒绝() {
            setAuthenticatedUser(1L);
            service.storeTranslationMemory("Hello", "", "zh", "google", 1L, null);
            verify(translationMemoryService, never()).storeTranslation(anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString());
        }

        @Test
        void 译文与原文相同被拒绝() {
            setAuthenticatedUser(1L);
            service.storeTranslationMemory("Hello world", "Hello world", "zh", "google", 1L, null);
            verify(translationMemoryService, never()).storeTranslation(anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString());
        }

        @Test
        void 长度比率过高被拒绝() {
            setAuthenticatedUser(1L);
            String longTarget = "这是一个非常非常非常非常非常非常非常非常非常长的翻译结果";
            service.storeTranslationMemory("Hi", longTarget, "zh", "google", 1L, null);
            verify(translationMemoryService, never()).storeTranslation(anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString());
        }

        @Test
        void 包含广告关键词被拒绝() {
            setAuthenticatedUser(1L);
            service.storeTranslationMemory("Hello", "Powered by ChatGPT", "zh", "google", 1L, null);
            verify(translationMemoryService, never()).storeTranslation(anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString());
        }

        @Test
        void 特殊字符比例过高被拒绝() {
            setAuthenticatedUser(1L);
            String specialText = "!@#$%^&*()_+!@#$%^&*()_+abc";
            service.storeTranslationMemory("Hello world test text here", specialText, "zh", "google", 1L, null);
            verify(translationMemoryService, never()).storeTranslation(anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString());
        }

        @Test
        void 质量通过则存储() {
            setAuthenticatedUser(1L);
            when(embeddingPort.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});
            when(translationMemoryService.searchByUserAndLang(anyLong(), anyString(), anyString(), anyInt()))
                    .thenReturn(Collections.emptyList());

            service.storeTranslationMemory("Hello world", "你好世界", "zh", "google", 1L, null);

            verify(translationMemoryService).storeTranslation(eq("Hello world"), eq("你好世界"),
                    eq("auto"), eq("zh"), eq(1L), isNull(), eq("google"), isNull());
        }
    }
}
