package com.yumu.noveltranslator.port.in;

import com.yumu.noveltranslator.port.dto.common.PageResponse;
import com.yumu.noveltranslator.port.dto.translation.GlossaryItemRequest;
import com.yumu.noveltranslator.port.dto.translation.GlossaryResponse;

import java.util.List;

/**
 * Glossary management use-case port.
 */
public interface GlossaryPort {

    GlossaryResponse createGlossaryItem(Long userId, GlossaryItemRequest request);

    GlossaryResponse updateGlossaryItem(Long userId, Long glossaryId, GlossaryItemRequest request);

    boolean deleteGlossaryItem(Long userId, Long glossaryId);

    PageResponse<GlossaryResponse> listGlossaries(Long userId, int page, int pageSize, String search);

    GlossaryResponse getGlossaryDetail(Long userId, Long glossaryId);

    List<GlossaryResponse> getAllGlossaryTerms(Long userId);

    int importGlossaryCsv(Long userId, List<GlossaryItemRequest> items);
}
