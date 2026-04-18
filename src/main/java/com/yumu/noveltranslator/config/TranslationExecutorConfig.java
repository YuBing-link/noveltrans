package com.yumu.noveltranslator.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * 翻译执行器配置
 * 提供全局共享的虚拟线程池，避免每次请求创建新线程池的开销
 */
@Configuration
@Slf4j
public class TranslationExecutorConfig {

    /**
     * 全局共享的虚拟线程池
     * 用于阅读器模式等需要并发翻译的场景
     *
     * 配置说明：
     * - 虚拟线程：适合 IO 密集型任务，如 HTTP 请求
     * - 无界队列：允许排队等待的任务
     * - 未捕获异常处理器：记录线程异常
     */
    @Bean(name = "translationExecutor")
    public ExecutorService translationExecutor() {
        // 创建虚拟线程工厂
        ThreadFactory factory = Thread.ofVirtual()
                .name("translation-worker-", 1)
                .uncaughtExceptionHandler((t, e) ->
                    log.error("虚拟线程 {} 未捕获异常：{}", t.getName(), e.getMessage(), e))
                .factory();

        // 创建线程池
        return Executors.newThreadPerTaskExecutor(factory);
    }

    /**
     * 可选：创建有界虚拟线程池
     * 使用信号量限制最大并发数，防止过多并发请求压垮翻译服务
     *
     * 注意：当前未启用，如需使用请取消 @Bean 注释并根据需要调整 maxConcurrent 参数
     */
    // @Bean(name = "boundedTranslationExecutor")
    public ExecutorService boundedTranslationExecutor() {
        int maxConcurrent = 50; // 最大并发数，可根据实际情况调整

        // 信号量用于限制并发数
        Semaphore semaphore = new Semaphore(maxConcurrent);

        ThreadFactory factory = Thread.ofVirtual()
                .name("bounded-translation-worker-", 1)
                .factory();

        return new BoundedExecutorService(
            Executors.newThreadPerTaskExecutor(factory),
            semaphore
        );
    }

    /**
     * 有界执行器服务装饰类
     * 通过信号量限制最大并发任务数
     */
    public static class BoundedExecutorService extends ExecutorServiceAdapter {
        private final Semaphore semaphore;

        public BoundedExecutorService(ExecutorService executor, Semaphore semaphore) {
            super(executor);
            this.semaphore = semaphore;
        }

        @Override
        public void execute(Runnable command) {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待获取信号量时被打断", e);
            }
            try {
                getExecutor().execute(() -> {
                    try {
                        command.run();
                    } finally {
                        semaphore.release();
                    }
                });
            } catch (RejectedExecutionException e) {
                semaphore.release();
                throw e;
            }
        }

        @Override
        public Future<?> submit(Runnable task) {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待获取信号量时被打断", e);
            }
            return getExecutor().submit(() -> {
                try {
                    task.run();
                } finally {
                    semaphore.release();
                }
            });
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待获取信号量时被打断", e);
            }
            return getExecutor().submit(() -> {
                try {
                    return task.call();
                } finally {
                    semaphore.release();
                }
            });
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待获取信号量时被打断", e);
            }
            return getExecutor().submit(() -> {
                try {
                    task.run();
                    return result;
                } finally {
                    semaphore.release();
                }
            });
        }
    }

    /**
     * ExecutorService 适配器
     * 用于装饰现有的 ExecutorService
     */
    public abstract static class ExecutorServiceAdapter implements ExecutorService {
        private final ExecutorService executor;

        protected ExecutorServiceAdapter(ExecutorService executor) {
            this.executor = executor;
        }

        public ExecutorService getExecutor() {
            return executor;
        }

        @Override
        public void shutdown() {
            executor.shutdown();
        }

        @Override
        public boolean isShutdown() {
            return executor.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return executor.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return executor.awaitTermination(timeout, unit);
        }

        @Override
        public List<Runnable> shutdownNow() {
            return executor.shutdownNow();
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
            return executor.invokeAll(tasks);
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
            return executor.invokeAll(tasks, timeout, unit);
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
            return executor.invokeAny(tasks);
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return executor.invokeAny(tasks, timeout, unit);
        }
    }
}
