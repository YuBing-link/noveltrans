package com.yumu.noveltranslator.port.out;

import java.util.List;

public interface VectorStorePort {
    List<VectorSearchResult> vectorSearch(float[] queryVector, Long userId, String targetLang, List<String> allowedModes, int topK);
    void storeVector(Long memoryId, String sourceText, String targetText, String sourceLang, String targetLang, Long userId, String translationMode, float[] embedding);
    void clearAllVectors();
}
