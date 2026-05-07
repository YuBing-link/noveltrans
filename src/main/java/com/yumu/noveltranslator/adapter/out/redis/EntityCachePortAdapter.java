package com.yumu.noveltranslator.adapter.out.redis;

import com.yumu.noveltranslator.port.out.EntityCachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class EntityCachePortAdapter implements EntityCachePort {

    private final DocumentEntityCache documentEntityCache;

    @Override
    public Map<String, String> getEntityMap(Long userId, String documentId) {
        return documentEntityCache.getEntityMap(userId, documentId);
    }

    @Override
    public void mergeEntityMap(Long userId, String documentId, Map<String, String> entities) {
        documentEntityCache.mergeEntityMap(userId, documentId, entities);
    }
}
