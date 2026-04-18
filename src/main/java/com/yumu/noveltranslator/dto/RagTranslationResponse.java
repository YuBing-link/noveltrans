package com.yumu.noveltranslator.dto;

import lombok.Data;
import java.util.List;

@Data
public class RagTranslationResponse {
    private boolean directHit;
    private String translation;
    private Double similarity;
    private List<RagMatch> matches;

    @Data
    public static class RagMatch {
        private String sourceText;
        private String targetText;
        private Double similarity;
        private Long memoryId;
    }
}
