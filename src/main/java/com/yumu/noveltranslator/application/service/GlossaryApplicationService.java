package com.yumu.noveltranslator.application.service;

import com.yumu.noveltranslator.port.dto.common.PageResponse;
import com.yumu.noveltranslator.port.dto.translation.GlossaryItemRequest;
import com.yumu.noveltranslator.port.dto.translation.GlossaryResponse;
import com.yumu.noveltranslator.port.in.GlossaryPort;
import com.yumu.noveltranslator.port.out.CacheVersionPort;
import com.yumu.noveltranslator.port.out.TranslationCacheAdminPort;
import com.yumu.noveltranslator.port.in.UserPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GlossaryApplicationService implements GlossaryPort {

    private final UserPort userPort;
    private final TranslationCacheAdminPort translationCacheAdminPort;
    private final CacheVersionPort cacheVersionPort;

    @Override
    public GlossaryResponse createGlossaryItem(Long userId, GlossaryItemRequest request) {
        GlossaryResponse response = userPort.createGlossaryItem(userId, request);
        translationCacheAdminPort.invalidateKeysForTerm(request.getSourceWord());
        return response;
    }

    @Override
    public GlossaryResponse updateGlossaryItem(Long userId, Long glossaryId, GlossaryItemRequest request) {
        GlossaryResponse response = userPort.updateGlossaryItem(userId, glossaryId, request);
        if (response != null) {
            translationCacheAdminPort.invalidateKeysForTerm(request.getSourceWord());
        }
        return response;
    }

    @Override
    public boolean deleteGlossaryItem(Long userId, Long glossaryId) {
        boolean success = userPort.deleteGlossaryItem(userId, glossaryId);
        if (success) {
            cacheVersionPort.bumpAllVersions();
        }
        return success;
    }

    @Override
    public PageResponse<GlossaryResponse> listGlossaries(Long userId, int page, int pageSize, String search) {
        return userPort.getGlossaryList(userId, page, pageSize, search);
    }

    @Override
    public GlossaryResponse getGlossaryDetail(Long userId, Long glossaryId) {
        return userPort.getGlossaryDetail(userId, glossaryId);
    }

    @Override
    public List<GlossaryResponse> getAllGlossaryTerms(Long userId) {
        return userPort.getGlossaryTerms(userId);
    }

    @Override
    public int importGlossaryCsv(Long userId, List<GlossaryItemRequest> items) {
        int imported = userPort.batchImportGlossaryItems(userId, items);
        for (GlossaryItemRequest item : items) {
            if (item.getSourceWord() != null && !item.getSourceWord().isEmpty()) {
                translationCacheAdminPort.invalidateKeysForTerm(item.getSourceWord());
            }
        }
        return imported;
    }
}
