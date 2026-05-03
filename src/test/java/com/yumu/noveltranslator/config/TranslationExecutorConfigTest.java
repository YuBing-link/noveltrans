package com.yumu.noveltranslator.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TranslationExecutorConfig 虚拟线程测试")
class TranslationExecutorConfigTest {

    @Nested
    @DisplayName("translationExecutor bean")
    class TranslationExecutorBeanTests {

        @Test
        void bean创建成功() {
            TranslationExecutorConfig config = new TranslationExecutorConfig();
            ExecutorService executor = config.translationExecutor();
            assertNotNull(executor);
        }

        @Test
        void 执行任务使用虚拟线程() throws Exception {
            TranslationExecutorConfig config = new TranslationExecutorConfig();
            ExecutorService executor = config.translationExecutor();

            Thread[] threadHolder = new Thread[1];
            Future<?> future = executor.submit(() ->
                threadHolder[0] = Thread.currentThread());
            future.get(5, TimeUnit.SECONDS);

            assertNotNull(threadHolder[0]);
            assertTrue(threadHolder[0].isVirtual());
        }

        @Test
        void 线程名带前缀() throws Exception {
            TranslationExecutorConfig config = new TranslationExecutorConfig();
            ExecutorService executor = config.translationExecutor();

            Thread[] threadHolder = new Thread[1];
            Future<?> future = executor.submit(() ->
                threadHolder[0] = Thread.currentThread());
            future.get(5, TimeUnit.SECONDS);

            assertTrue(threadHolder[0].getName().startsWith("translation-worker-"));
        }

        @Test
        void 未捕获异常由handler处理() throws Exception {
            TranslationExecutorConfig config = new TranslationExecutorConfig();
            ExecutorService executor = config.translationExecutor();

            CountDownLatch latch = new CountDownLatch(1);
            // 使用 Thread.setDefaultUncaughtExceptionHandler 捕获
            Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
            try {
                Thread.setDefaultUncaughtExceptionHandler((t, e) -> latch.countDown());
                executor.submit(() -> { throw new RuntimeException("test"); });
                assertTrue(latch.await(5, TimeUnit.SECONDS));
            } finally {
                Thread.setDefaultUncaughtExceptionHandler(original);
            }
        }

        @Test
        void shutdown正常() {
            TranslationExecutorConfig config = new TranslationExecutorConfig();
            ExecutorService executor = config.translationExecutor();
            assertDoesNotThrow(() -> executor.shutdown());
        }
    }

    @Nested
    @DisplayName("BoundedExecutorService")
    class BoundedExecutorServiceTests {

        @Test
        void execute执行任务() throws Exception {
            Semaphore semaphore = new Semaphore(5);
            TranslationExecutorConfig.BoundedExecutorService bounded =
                new TranslationExecutorConfig.BoundedExecutorService(
                    Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory()), semaphore);

            CountDownLatch latch = new CountDownLatch(1);
            bounded.execute(latch::countDown);
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            bounded.shutdown();
        }

        @Test
        void submitRunnable返回Future() throws Exception {
            Semaphore semaphore = new Semaphore(5);
            TranslationExecutorConfig.BoundedExecutorService bounded =
                new TranslationExecutorConfig.BoundedExecutorService(
                    Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory()), semaphore);

            Future<?> future = bounded.submit(() -> {});
            assertNotNull(future.get(5, TimeUnit.SECONDS));
            bounded.shutdown();
        }

        @Test
        void submitCallable返回结果() throws Exception {
            Semaphore semaphore = new Semaphore(5);
            TranslationExecutorConfig.BoundedExecutorService bounded =
                new TranslationExecutorConfig.BoundedExecutorService(
                    Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory()), semaphore);

            Future<String> future = bounded.submit(() -> "result");
            assertEquals("result", future.get(5, TimeUnit.SECONDS));
            bounded.shutdown();
        }

        @Test
        void submitRunnableWithResult返回指定值() throws Exception {
            Semaphore semaphore = new Semaphore(5);
            TranslationExecutorConfig.BoundedExecutorService bounded =
                new TranslationExecutorConfig.BoundedExecutorService(
                    Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory()), semaphore);

            Future<Integer> future = bounded.submit(() -> {}, 42);
            assertEquals(42, future.get(5, TimeUnit.SECONDS));
            bounded.shutdown();
        }

        @Test
        void 信号量限制并发数() throws Exception {
            int maxConcurrent = 3;
            Semaphore semaphore = new Semaphore(maxConcurrent);
            TranslationExecutorConfig.BoundedExecutorService bounded =
                new TranslationExecutorConfig.BoundedExecutorService(
                    Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory()), semaphore);

            CountDownLatch allRunning = new CountDownLatch(maxConcurrent);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger peakConcurrent = new AtomicInteger(0);
            AtomicInteger current = new AtomicInteger(0);

            for (int i = 0; i < maxConcurrent; i++) {
                bounded.submit(() -> {
                    int c = current.incrementAndGet();
                    peakConcurrent.accumulateAndGet(c, Math::max);
                    allRunning.countDown();
                    try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    current.decrementAndGet();
                });
            }

            assertTrue(allRunning.await(5, TimeUnit.SECONDS));
            assertEquals(maxConcurrent, peakConcurrent.get());

            // 释放后任务完成，信号量恢复
            release.countDown();
            Thread.sleep(500);
            assertEquals(maxConcurrent, semaphore.availablePermits());

            // 额外任务可以正常执行
            CountDownLatch extraDone = new CountDownLatch(1);
            bounded.submit(() -> extraDone.countDown());
            assertTrue(extraDone.await(5, TimeUnit.SECONDS));
            bounded.shutdown();
        }

        @Test
        void 任务完成释放信号量() throws Exception {
            Semaphore semaphore = new Semaphore(1);
            TranslationExecutorConfig.BoundedExecutorService bounded =
                new TranslationExecutorConfig.BoundedExecutorService(
                    Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory()), semaphore);

            // 消耗掉唯一的 permit
            semaphore.acquire();
            assertEquals(0, semaphore.availablePermits());

            CountDownLatch taskDone = new CountDownLatch(1);
            bounded.execute(taskDone::countDown);
            assertTrue(taskDone.await(5, TimeUnit.SECONDS));

            // 任务完成后 permit 应被释放
            assertEquals(1, semaphore.availablePermits());
            bounded.shutdown();
        }

        @Test
        void 任务异常也释放信号量_execute() throws Exception {
            Semaphore semaphore = new Semaphore(1);
            TranslationExecutorConfig.BoundedExecutorService bounded =
                new TranslationExecutorConfig.BoundedExecutorService(
                    Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory()), semaphore);

            semaphore.acquire();
            assertEquals(0, semaphore.availablePermits());

            CountDownLatch taskAttempted = new CountDownLatch(1);
            bounded.execute(() -> {
                try { throw new RuntimeException("fail"); }
                finally { taskAttempted.countDown(); }
            });
            assertTrue(taskAttempted.await(5, TimeUnit.SECONDS));

            assertEquals(1, semaphore.availablePermits());
            bounded.shutdown();
        }

        @Test
        void submitRunnable异常释放信号量() throws Exception {
            Semaphore semaphore = new Semaphore(1);
            TranslationExecutorConfig.BoundedExecutorService bounded =
                new TranslationExecutorConfig.BoundedExecutorService(
                    Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory()), semaphore);

            semaphore.acquire();
            CountDownLatch taskAttempted = new CountDownLatch(1);
            Future<?> future = bounded.submit(() -> {
                try { throw new IllegalStateException("submit fail"); }
                finally { taskAttempted.countDown(); }
            });
            assertTrue(taskAttempted.await(5, TimeUnit.SECONDS));
            assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
            assertEquals(1, semaphore.availablePermits());
            bounded.shutdown();
        }

        @Test
        void submitCallable异常释放信号量() throws Exception {
            Semaphore semaphore = new Semaphore(1);
            TranslationExecutorConfig.BoundedExecutorService bounded =
                new TranslationExecutorConfig.BoundedExecutorService(
                    Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory()), semaphore);

            semaphore.acquire();
            Future<String> future = bounded.submit(() -> { throw new RuntimeException("callable fail"); });
            assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
            assertEquals(1, semaphore.availablePermits());
            bounded.shutdown();
        }

        @Test
        void submitRunnableWithResult异常释放信号量() throws Exception {
            Semaphore semaphore = new Semaphore(1);
            TranslationExecutorConfig.BoundedExecutorService bounded =
                new TranslationExecutorConfig.BoundedExecutorService(
                    Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory()), semaphore);

            semaphore.acquire();
            Future<Integer> future = bounded.submit(() -> { throw new RuntimeException("submit result fail"); }, 42);
            assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
            assertEquals(1, semaphore.availablePermits());
            bounded.shutdown();
        }

        @Test
        void acquire中断抛出RuntimeException_execute() throws Exception {
            Semaphore semaphore = new Semaphore(0);
            TranslationExecutorConfig.BoundedExecutorService bounded =
                new TranslationExecutorConfig.BoundedExecutorService(
                    Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory()), semaphore);

            CountDownLatch interrupted = new CountDownLatch(1);
            Thread t = Thread.ofVirtual().start(() -> {
                try { bounded.execute(() -> {}); }
                catch (RuntimeException e) { interrupted.countDown(); }
            });
            Thread.sleep(100);
            t.interrupt();
            assertTrue(interrupted.await(5, TimeUnit.SECONDS));
            bounded.shutdownNow();
        }

        @Test
        void acquire中断抛出RuntimeException_submitRunnable() throws Exception {
            Semaphore semaphore = new Semaphore(0);
            TranslationExecutorConfig.BoundedExecutorService bounded =
                new TranslationExecutorConfig.BoundedExecutorService(
                    Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory()), semaphore);

            CountDownLatch interrupted = new CountDownLatch(1);
            Thread t = Thread.ofVirtual().start(() -> {
                try { bounded.submit(() -> {}); }
                catch (RuntimeException e) { interrupted.countDown(); }
            });
            Thread.sleep(100);
            t.interrupt();
            assertTrue(interrupted.await(5, TimeUnit.SECONDS));
            bounded.shutdownNow();
        }

        @Test
        void acquire中断抛出RuntimeException_submitCallable() throws Exception {
            Semaphore semaphore = new Semaphore(0);
            TranslationExecutorConfig.BoundedExecutorService bounded =
                new TranslationExecutorConfig.BoundedExecutorService(
                    Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory()), semaphore);

            CountDownLatch interrupted = new CountDownLatch(1);
            Thread t = Thread.ofVirtual().start(() -> {
                try { bounded.submit(() -> "x"); }
                catch (RuntimeException e) { interrupted.countDown(); }
            });
            Thread.sleep(100);
            t.interrupt();
            assertTrue(interrupted.await(5, TimeUnit.SECONDS));
            bounded.shutdownNow();
        }

        @Test
        void acquire中断抛出RuntimeException_submitWithResult() throws Exception {
            Semaphore semaphore = new Semaphore(0);
            TranslationExecutorConfig.BoundedExecutorService bounded =
                new TranslationExecutorConfig.BoundedExecutorService(
                    Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory()), semaphore);

            CountDownLatch interrupted = new CountDownLatch(1);
            Thread t = Thread.ofVirtual().start(() -> {
                try { bounded.submit(() -> {}, 1); }
                catch (RuntimeException e) { interrupted.countDown(); }
            });
            Thread.sleep(100);
            t.interrupt();
            assertTrue(interrupted.await(5, TimeUnit.SECONDS));
            bounded.shutdownNow();
        }
    }

    @Nested
    @DisplayName("ExecutorServiceAdapter")
    class ExecutorServiceAdapterTests {

        @Test
        void shutdown委托给底层executor() {
            ExecutorService mock = Executors.newVirtualThreadPerTaskExecutor();
            TranslationExecutorConfig.ExecutorServiceAdapter adapter =
                new TranslationExecutorConfig.ExecutorServiceAdapter(mock) {};

            assertFalse(adapter.isShutdown());
            adapter.shutdown();
            assertTrue(adapter.isShutdown());
        }

        @Test
        void isShutdown委托给底层executor() {
            ExecutorService mock = Executors.newVirtualThreadPerTaskExecutor();
            TranslationExecutorConfig.ExecutorServiceAdapter adapter =
                new TranslationExecutorConfig.ExecutorServiceAdapter(mock) {};

            assertFalse(adapter.isShutdown());
            mock.shutdown();
            assertTrue(adapter.isShutdown());
        }

        @Test
        void isTerminated委托给底层executor() throws Exception {
            ExecutorService mock = Executors.newVirtualThreadPerTaskExecutor();
            TranslationExecutorConfig.ExecutorServiceAdapter adapter =
                new TranslationExecutorConfig.ExecutorServiceAdapter(mock) {};

            assertFalse(adapter.isTerminated());
            mock.shutdown();
            mock.awaitTermination(5, TimeUnit.SECONDS);
            assertTrue(adapter.isTerminated());
        }

        @Test
        void awaitTermination委托给底层executor() throws Exception {
            ExecutorService mock = Executors.newVirtualThreadPerTaskExecutor();
            TranslationExecutorConfig.ExecutorServiceAdapter adapter =
                new TranslationExecutorConfig.ExecutorServiceAdapter(mock) {};

            mock.submit(() -> {}).get();
            mock.shutdown();

            assertTrue(adapter.awaitTermination(5, TimeUnit.SECONDS));
        }

        @Test
        void shutdownNow委托给底层executor() {
            ExecutorService mock = Executors.newVirtualThreadPerTaskExecutor();
            TranslationExecutorConfig.ExecutorServiceAdapter adapter =
                new TranslationExecutorConfig.ExecutorServiceAdapter(mock) {};

            List<Runnable> remaining = adapter.shutdownNow();
            assertTrue(adapter.isShutdown());
            assertNotNull(remaining);
        }

        @Test
        void invokeAll无超时委托给底层executor() throws Exception {
            ExecutorService mock = Executors.newVirtualThreadPerTaskExecutor();
            TranslationExecutorConfig.ExecutorServiceAdapter adapter =
                new TranslationExecutorConfig.ExecutorServiceAdapter(mock) {};

            List<Future<String>> results = adapter.invokeAll(
                List.of(() -> "a", () -> "b"));

            assertEquals("a", results.get(0).get());
            assertEquals("b", results.get(1).get());
            adapter.shutdown();
        }

        @Test
        void invokeAll带超时委托给底层executor() throws Exception {
            ExecutorService mock = Executors.newVirtualThreadPerTaskExecutor();
            TranslationExecutorConfig.ExecutorServiceAdapter adapter =
                new TranslationExecutorConfig.ExecutorServiceAdapter(mock) {};

            List<Future<String>> results = adapter.invokeAll(
                List.of(() -> "x"), 5, TimeUnit.SECONDS);

            assertEquals("x", results.get(0).get());
            adapter.shutdown();
        }

        @Test
        void invokeAny无超时返回第一个成功结果() throws Exception {
            ExecutorService mock = Executors.newVirtualThreadPerTaskExecutor();
            TranslationExecutorConfig.ExecutorServiceAdapter adapter =
                new TranslationExecutorConfig.ExecutorServiceAdapter(mock) {};

            String result = adapter.invokeAny(
                List.of(() -> { Thread.sleep(1000); return "slow"; },
                        () -> "fast"));

            assertNotNull(result);
            adapter.shutdown();
        }

        @Test
        void invokeAny带超时返回结果() throws Exception {
            ExecutorService mock = Executors.newVirtualThreadPerTaskExecutor();
            TranslationExecutorConfig.ExecutorServiceAdapter adapter =
                new TranslationExecutorConfig.ExecutorServiceAdapter(mock) {};

            String result = adapter.invokeAny(
                List.of(() -> "result"), 5, TimeUnit.SECONDS);

            assertEquals("result", result);
            adapter.shutdown();
        }

        @Test
        void getExecutor返回底层executor() {
            ExecutorService mock = Executors.newVirtualThreadPerTaskExecutor();
            TranslationExecutorConfig.ExecutorServiceAdapter adapter =
                new TranslationExecutorConfig.ExecutorServiceAdapter(mock) {};

            assertSame(mock, adapter.getExecutor());
        }
    }
}
