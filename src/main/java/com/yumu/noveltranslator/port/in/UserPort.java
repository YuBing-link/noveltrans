package com.yumu.noveltranslator.port.in;

import com.yumu.noveltranslator.domain.model.User;
import com.yumu.noveltranslator.port.dto.common.PageResponse;
import com.yumu.noveltranslator.port.dto.entity.PlatformStatsResponse;
import com.yumu.noveltranslator.port.dto.entity.TranslationHistoryResponse;
import com.yumu.noveltranslator.port.dto.entity.UserPreferencesRequest;
import com.yumu.noveltranslator.port.dto.entity.UserPreferencesResponse;
import com.yumu.noveltranslator.port.dto.entity.UserQuotaResponse;
import com.yumu.noveltranslator.port.dto.entity.UserStatisticsResponse;
import com.yumu.noveltranslator.port.dto.translation.GlossaryItemRequest;
import com.yumu.noveltranslator.port.dto.translation.GlossaryResponse;

import java.util.List;

public interface UserPort {
    void updateUser(User user);
    UserStatisticsResponse getUserStatistics(Long userId);
    UserQuotaResponse getUserQuota(User user);
    PageResponse<GlossaryResponse> getGlossaryList(Long userId, int page, int pageSize, String search);
    GlossaryResponse getGlossaryDetail(Long userId, Long glossaryId);
    List<GlossaryResponse> getGlossaryTerms(Long userId);
    GlossaryResponse createGlossaryItem(Long userId, GlossaryItemRequest request);
    GlossaryResponse updateGlossaryItem(Long userId, Long glossaryId, GlossaryItemRequest request);
    boolean deleteGlossaryItem(Long userId, Long glossaryId);
    int batchImportGlossaryItems(Long userId, List<GlossaryItemRequest> items);
    UserPreferencesResponse getUserPreferences(Long userId);
    UserPreferencesResponse updateUserPreferences(Long userId, UserPreferencesRequest request);
    PlatformStatsResponse getPlatformStats();
    PageResponse<TranslationHistoryResponse> getTranslationHistory(Long userId, int page, int pageSize, String type);
}
