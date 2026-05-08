package com.yumu.noveltranslator.port.out;

public interface ApiKeyCachePort {
    void invalidate(String key);
}
