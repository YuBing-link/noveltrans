package com.yumu.noveltranslator.config;

import com.yumu.noveltranslator.event.CacheInvalidationEvent;
import com.yumu.noveltranslator.service.CacheVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis pub/sub 配置
 * 用于多实例间的缓存失效联动
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class CachePubSubConfig {

    private static final String INVALIDATION_CHANNEL = "translator:cache:invalidation";

    private final CacheVersionService cacheVersionService;

    @Bean
    public ChannelTopic cacheInvalidationTopic() {
        return new ChannelTopic(INVALIDATION_CHANNEL);
    }

    @Bean
    public MessageListener cacheInvalidationListener() {
        return (message, pattern) -> {
            try {
                String body = message.getBody() != null ? new String(message.getBody()) : "";
                CacheInvalidationEvent event = CacheInvalidationEvent.deserialize(body);
                cacheVersionService.handleInvalidationEvent(event.getSourceLang(), event.getTargetLang());
            } catch (Exception e) {
                log.warn("处理缓存失效事件失败: {}", e.getMessage());
            }
        };
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListener cacheInvalidationListener,
            ChannelTopic cacheInvalidationTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(cacheInvalidationListener, cacheInvalidationTopic);
        log.info("Redis pub/sub 监听已启动，频道: {}", INVALIDATION_CHANNEL);
        return container;
    }
}
