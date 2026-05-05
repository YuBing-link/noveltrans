package com.yumu.noveltranslator.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 章节拆分异步执行器配置
 */
@Configuration
@Slf4j
public class ChapterSplitExecutorConfig {

    /**
     * 章节拆分专用线程池
     * 用于异步批量插入章节，避免阻塞 HTTP 请求线程。
     */
    @Bean(name = "chapterSplitExecutor")
    public Executor chapterSplitExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("chapter-split-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
