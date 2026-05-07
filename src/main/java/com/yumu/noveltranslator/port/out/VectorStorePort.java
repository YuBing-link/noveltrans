package com.yumu.noveltranslator.port.out;

import java.util.List;
import java.util.Map;

public interface VectorStorePort {
    List<Map<String, String>> vectorSearch(float[] queryVector, Long userId, String targetLang, List<String> allowedModes, int topK);
    void storeVector(String key, Map<String, String> fields);
    void clearAllVectors();
}
