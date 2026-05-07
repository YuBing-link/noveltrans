package com.yumu.noveltranslator.adapter.out.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yumu.noveltranslator.port.out.CachePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * Redis-based CachePort adapter using StringRedisTemplate + Jackson serialization.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisCacheAdapter implements CachePort {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            if (type == String.class) {
                return Optional.of(type.cast(value));
            }
            return Optional.of(objectMapper.readValue(value, type));
        } catch (JsonProcessingException e) {
            log.warn("Cache deserialization failed for key={}, type={}: {}", key, type.getName(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, Object value, int ttlSeconds) {
        try {
            String json = (value instanceof String) ? (String) value : objectMapper.writeValueAsString(value);
            stringRedisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds));
        } catch (JsonProcessingException e) {
            log.error("Cache serialization failed for key={}: {}", key, e.getMessage(), e);
        }
    }

    @Override
    public void evict(String key) {
        stringRedisTemplate.delete(key);
    }

    @Override
    public void evictAll(String pattern) {
        Set<String> keys = stringRedisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }
}
