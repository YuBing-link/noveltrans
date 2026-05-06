package com.yumu.noveltranslator.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * 计量日志异步写入执行器配置
 * 用于 quota_usage 日志的异步写入，与主业务线程池隔离
 */
@Configuration
@Slf4j
public class MeteringExecutorConfig {

    /**
     * 计量日志专用虚拟线程池
     * 核心线程数少，适合低优先级的异步日志写入
     */
    @Bean(name = "meteringExecutor")
    public ExecutorService meteringExecutor() {
        ThreadFactory factory = Thread.ofVirtual()
                .name("metering-worker-", 1)
                .uncaughtExceptionHandler((t, e) ->
                    log.error("计量线程 {} 未捕获异常：{}", t.getName(), e.getMessage(), e))
                .factory();

        return Executors.newThreadPerTaskExecutor(factory);
    }
}
