package com.yumu.noveltranslator.port.out;

import java.time.LocalDate;

/**
 * 端口：配额存储操作（Redis Lua 脚本 + MySQL 异步持久化）。
 */
public interface QuotaPort {
    boolean tryConsumeChars(String key, long quota, long cost, int ttl);
    long refundChars(String key, long refundAmount);
    void incrementDailyUsage(Long userId, LocalDate date, long cost);
    void decrementDailyUsage(Long userId, LocalDate date, long amount);
}
