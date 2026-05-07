package com.yumu.noveltranslator.port.out;

import java.util.Map;

public interface EntityCachePort {
    Map<String, String> getEntityMap(Long userId, String documentId);
    void mergeEntityMap(Long userId, String documentId, Map<String, String> entities);
}
