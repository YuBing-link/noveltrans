package com.yumu.noveltranslator.config;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BoundedExecutorServiceTest {

    @Test
    void boundedExecutorServiceLimitsConcurrency() throws InterruptedException {
        int maxConcurrent = 2;
        Semaphore semaphore = new Semaphore(maxConcurrent);
        ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
        TranslationExecutorConfig.BoundedExecutorService executor =
                new TranslationExecutorConfig.BoundedExecutorService(delegate, semaphore);

        try {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(4);
            AtomicInteger runningCount = new AtomicInteger(0);
            AtomicInteger maxSeen = new AtomicInteger(0);

            for (int i = 0; i < 4; i++) {
                executor.submit(() -> {
                    int current = runningCount.incrementAndGet();
                    maxSeen.set(Math.max(maxSeen.get(), current));
                    try {
                        started.countDown();
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        runningCount.decrementAndGet();
                        done.countDown();
                    }
                });
            }

            started.await();
            Thread.sleep(200);
            assertTrue(maxSeen.get() <= maxConcurrent);
            done.await(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
            delegate.shutdown();
        }
    }

    @Test
    void executeWithSemaphoreInterruption() {
        int maxConcurrent = 1;
        Semaphore semaphore = new Semaphore(maxConcurrent);
        semaphore.acquireUninterruptibly(1);

        ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
        TranslationExecutorConfig.BoundedExecutorService executor =
                new TranslationExecutorConfig.BoundedExecutorService(delegate, semaphore);

        Thread t = Thread.ofVirtual().start(() -> {
            executor.execute(() -> {});
        });
        t.interrupt();

        try {
            t.join(2000);
            assertFalse(t.isAlive());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
            delegate.shutdown();
        }
    }

    @Test
    void submitCallable() throws Exception {
        Semaphore semaphore = new Semaphore(5);
        ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
        TranslationExecutorConfig.BoundedExecutorService executor =
                new TranslationExecutorConfig.BoundedExecutorService(delegate, semaphore);

        try {
            Future<String> future = executor.submit(() -> "hello");
            assertEquals("hello", future.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdown();
            delegate.shutdown();
        }
    }

    @Test
    void submitRunnableWithResult() throws Exception {
        Semaphore semaphore = new Semaphore(5);
        ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
        TranslationExecutorConfig.BoundedExecutorService executor =
                new TranslationExecutorConfig.BoundedExecutorService(delegate, semaphore);

        try {
            Future<String> future = executor.submit(() -> {}, "result");
            assertEquals("result", future.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdown();
            delegate.shutdown();
        }
    }

    @Test
    void shutdownAndAwaitTermination() throws InterruptedException {
        Semaphore semaphore = new Semaphore(5);
        ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
        TranslationExecutorConfig.BoundedExecutorService executor =
                new TranslationExecutorConfig.BoundedExecutorService(delegate, semaphore);

        assertFalse(executor.isShutdown());
        executor.shutdown();
        assertTrue(executor.isShutdown());
        assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
    }

    @Test
    void shutdownNow() {
        Semaphore semaphore = new Semaphore(5);
        ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
        TranslationExecutorConfig.BoundedExecutorService executor =
                new TranslationExecutorConfig.BoundedExecutorService(delegate, semaphore);

        var remaining = executor.shutdownNow();
        assertTrue(executor.isShutdown());
        delegate.shutdown();
    }

    @Test
    void getExecutorReturnsDelegate() {
        ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
        Semaphore semaphore = new Semaphore(5);
        TranslationExecutorConfig.BoundedExecutorService executor =
                new TranslationExecutorConfig.BoundedExecutorService(delegate, semaphore);

        assertSame(delegate, executor.getExecutor());
        executor.shutdown();
    }
}
