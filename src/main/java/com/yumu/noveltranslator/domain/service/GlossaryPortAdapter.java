package com.yumu.noveltranslator.domain.service;

import com.yumu.noveltranslator.adapter.out.redis.CacheVersionService;
import com.yumu.noveltranslator.adapter.out.redis.TranslationCacheService;
import com.yumu.noveltranslator.dto.common.PageResponse;
import com.yumu.noveltranslator.dto.translation.GlossaryItemRequest;
import com.yumu.noveltranslator.dto.translation.GlossaryResponse;
import com.yumu.noveltranslator.port.in.GlossaryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GlossaryPortAdapter implements GlossaryPort {

    private final UserService userService;
    private final TranslationCacheService translationCacheService;
    private final CacheVersionService cacheVersionService;

    @Override
    public GlossaryResponse createGlossaryItem(Long userId, GlossaryItemRequest request) {
        GlossaryResponse response = userService.createGlossaryItem(userId, request);
        translationCacheService.invalidateKeysForTerm(request.getSourceWord());
        return response;
    }

    @Override
    public GlossaryResponse updateGlossaryItem(Long userId, Long glossaryId, GlossaryItemRequest request) {
        GlossaryResponse response = userService.updateGlossaryItem(userId, glossaryId, request);
        if (response != null) {
            translationCacheService.invalidateKeysForTerm(request.getSourceWord());
        }
        return response;
    }

    @Override
    public boolean deleteGlossaryItem(Long userId, Long glossaryId) {
        boolean success = userService.deleteGlossaryItem(userId, glossaryId);
        if (success) {
            cacheVersionService.bumpAllVersions();
        }
        return success;
    }

    @Override
    public PageResponse<GlossaryResponse> listGlossaries(Long userId, int page, int pageSize, String search) {
        return userService.getGlossaryList(userId, page, pageSize, search);
    }

    @Override
    public GlossaryResponse getGlossaryDetail(Long userId, Long glossaryId) {
        return userService.getGlossaryDetail(userId, glossaryId);
    }

    @Override
    public List<GlossaryResponse> getAllGlossaryTerms(Long userId) {
        return userService.getGlossaryTerms(userId);
    }

    @Override
    public int importGlossaryCsv(Long userId, List<GlossaryItemRequest> items) {
        int imported = userService.batchImportGlossaryItems(userId, items);
        for (GlossaryItemRequest item : items) {
            if (item.getSourceWord() != null && !item.getSourceWord().isEmpty()) {
                translationCacheService.invalidateKeysForTerm(item.getSourceWord());
            }
        }
        return imported;
    }
}
