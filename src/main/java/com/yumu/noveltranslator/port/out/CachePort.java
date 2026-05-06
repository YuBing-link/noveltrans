package com.yumu.noveltranslator.port.out;

import java.util.Optional;

public interface CachePort {
    <T> Optional<T> get(String key, Class<T> type);
    void put(String key, Object value, int ttlSeconds);
    void evict(String key);
    void evictAll(String pattern);
}
