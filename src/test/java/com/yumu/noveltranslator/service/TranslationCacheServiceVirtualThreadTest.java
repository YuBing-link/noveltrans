package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.entity.TranslationCache;
import com.yumu.noveltranslator.mapper.TranslationCacheMapper;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TranslationCacheService 虚拟线程测试
 * 覆盖 saveToDatabaseAsync 的 Thread.startVirtualThread 异步写入行为
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TranslationCacheService 虚拟线程异步写入测试")
class TranslationCacheServiceVirtualThreadTest {

    @Mock
    private TranslationCacheMapper translationCacheMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private TranslationCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new TranslationCacheService(translationCacheMapper, stringRedisTemplate);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        doNothing().when(valueOperations).set(anyString(), anyString(), any(Duration.class));
    }

    @Nested
    @DisplayName("saveToDatabaseAsync 虚拟线程异步写入")
    class SaveToDatabaseAsyncTests {

        @Test
        void putCache触发异步数据库写入() throws Exception {
            cacheService.putCache("vt-key", "source", "target", "en", "zh", "google");

            Thread.sleep(2000);

            ArgumentCaptor<TranslationCache> captor = ArgumentCaptor.forClass(TranslationCache.class);
            verify(translationCacheMapper).insertCache(captor.capture());

            TranslationCache saved = captor.getValue();
            assertEquals("vt-key", saved.getCacheKey());
            assertEquals("source", saved.getSourceText());
            assertEquals("target", saved.getTargetText());
            assertEquals("en", saved.getSourceLang());
            assertEquals("zh", saved.getTargetLang());
            assertEquals("google", saved.getEngine());
            assertNotNull(saved.getExpireTime());
        }

        @Test
        void 第一次失败第二次重试成功() throws Exception {
            doThrow(new RuntimeException("DB timeout"))
                .doNothing()
                .when(translationCacheMapper).insertCache(any());

            cacheService.putCache("first-retry-key", "src", "tgt", "en", "zh", "google");

            // 等待第一次失败后的重试（sleep 500ms + buffer）
            Thread.sleep(2000);

            verify(translationCacheMapper, times(2)).insertCache(any());
        }

        @Test
        void 数据库写入失败后重试() throws Exception {
            doThrow(new RuntimeException("DB timeout"))
                .doThrow(new RuntimeException("DB timeout"))
                .doNothing()
                .when(translationCacheMapper).insertCache(any());

            cacheService.putCache("retry-key", "src", "tgt", "en", "zh", "google");

            Thread.sleep(3000);

            verify(translationCacheMapper, times(3)).insertCache(any());
        }

        @Test
        void 超过最大重试次数后放弃() throws Exception {
            doThrow(new RuntimeException("permanent failure"))
                .when(translationCacheMapper).insertCache(any());

            cacheService.putCache("fail-key", "src", "tgt", "en", "zh", "google");

            Thread.sleep(3000);

            verify(translationCacheMapper, times(3)).insertCache(any());
        }

        @Test
        void 重试sleep期间被中断() throws Exception {
            doThrow(new RuntimeException("DB timeout"))
                .when(translationCacheMapper).insertCache(any());

            CountDownLatch mapperCalled = new CountDownLatch(1);
            AtomicReference<Thread> virtualThread = new AtomicReference<>();
            doAnswer(invocation -> {
                virtualThread.set(Thread.currentThread());
                mapperCalled.countDown();
                throw new RuntimeException("DB timeout");
            }).when(translationCacheMapper).insertCache(any());

            cacheService.putCache("interrupt-key", "src", "tgt", "en", "zh", "google");
            mapperCalled.await(5, TimeUnit.SECONDS);
            Thread.sleep(50); // 确保线程已进入 sleep(500)
            virtualThread.get().interrupt();

            // 等待确认不会再调用（中断后不再重试）
            Thread.sleep(1500);

            // 仅调用一次（首次失败后中断，没有重试）
            verify(translationCacheMapper, times(1)).insertCache(any());
        }

        @Test
        void 异步写入不阻塞putCache调用() {
            long start = System.currentTimeMillis();
            cacheService.putCache("blocking-key", "src", "tgt", "en", "zh", "google");
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(elapsed < 500, "putCache took " + elapsed + "ms, should be near-instant");
        }

        @Test
        void 虚拟线程确实是虚拟线程() throws Exception {
            Thread mainThread = Thread.currentThread();
            Thread[] capturedThread = new Thread[1];

            CountDownLatch latch = new CountDownLatch(1);
            doAnswer(invocation -> {
                capturedThread[0] = Thread.currentThread();
                latch.countDown();
                return null;
            }).when(translationCacheMapper).insertCache(any());

            cacheService.putCache("thread-check-key", "src", "tgt", "en", "zh", "google");

            latch.await(5, TimeUnit.SECONDS);

            assertNotNull(capturedThread[0]);
            assertNotSame(mainThread, capturedThread[0]);
            assertTrue(capturedThread[0].isVirtual(),
                "Expected virtual thread but got: " + capturedThread[0].getClass().getName());
        }
    }

    @Nested
    @DisplayName("putCache with mode 异步写入")
    class PutCacheWithModeAsyncTests {

        @Test
        void 带模式标签的异步写入() throws Exception {
            cacheService.putCache("mode-key", "src", "tgt", "en", "zh", "google", "expert");

            Thread.sleep(2000);

            ArgumentCaptor<TranslationCache> captor = ArgumentCaptor.forClass(TranslationCache.class);
            verify(translationCacheMapper).insertCache(captor.capture());

            assertEquals("mode-key_expert", captor.getValue().getCacheKey());
        }
    }
}
