package com.yumu.noveltranslator.config;

import com.yumu.noveltranslator.domain.event.CacheInvalidationEvent;
import com.yumu.noveltranslator.adapter.out.redis.ApiKeyCacheService;
import com.yumu.noveltranslator.adapter.out.redis.CacheVersionService;
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
@Slf4j
public class CachePubSubConfig {

    private static final String INVALIDATION_CHANNEL = "translator:cache:invalidation";
    private static final String APIKEY_INVALIDATION_CHANNEL = "apikey:invalidation";

    private final CacheVersionService cacheVersionService;
    private final ApiKeyCacheService apiKeyCacheService;

    public CachePubSubConfig(CacheVersionService cacheVersionService, ApiKeyCacheService apiKeyCacheService) {
        this.cacheVersionService = cacheVersionService;
        this.apiKeyCacheService = apiKeyCacheService;
    }

    @Bean
    public ChannelTopic cacheInvalidationTopic() {
        return new ChannelTopic(INVALIDATION_CHANNEL);
    }

    @Bean
    public ChannelTopic apiKeyInvalidationTopic() {
        return new ChannelTopic(APIKEY_INVALIDATION_CHANNEL);
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
    public MessageListener apiKeyInvalidationListener() {
        return (message, pattern) -> {
            try {
                String token = message.getBody() != null ? new String(message.getBody()) : "";
                if (!token.isEmpty()) {
                    apiKeyCacheService.getLocalCache().invalidate(token);
                    log.debug("API Key 跨实例 L1 缓存失效: token={}", maskToken(token));
                }
            } catch (Exception e) {
                log.warn("处理 API Key 缓存失效事件失败: {}", e.getMessage());
            }
        };
    }

    private String maskToken(String token) {
        if (token.length() < 16) return "***";
        return token.substring(0, 10) + "..." + token.substring(token.length() - 4);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListener cacheInvalidationListener,
            MessageListener apiKeyInvalidationListener,
            ChannelTopic cacheInvalidationTopic,
            ChannelTopic apiKeyInvalidationTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(cacheInvalidationListener, cacheInvalidationTopic);
        container.addMessageListener(apiKeyInvalidationListener, apiKeyInvalidationTopic);
        log.info("Redis pub/sub 监听已启动，频道: {}", INVALIDATION_CHANNEL);
        return container;
    }
}
