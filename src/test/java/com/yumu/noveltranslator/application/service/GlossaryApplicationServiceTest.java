package com.yumu.noveltranslator.application.service;

import com.yumu.noveltranslator.port.dto.common.PageResponse;
import com.yumu.noveltranslator.port.dto.translation.GlossaryItemRequest;
import com.yumu.noveltranslator.port.dto.translation.GlossaryResponse;
import com.yumu.noveltranslator.port.in.UserPort;
import com.yumu.noveltranslator.port.out.CacheVersionPort;
import com.yumu.noveltranslator.port.out.TranslationCacheAdminPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlossaryApplicationServiceTest {

    @Mock
    private UserPort userPort;

    @Mock
    private TranslationCacheAdminPort translationCacheAdminPort;

    @Mock
    private CacheVersionPort cacheVersionPort;

    private GlossaryApplicationService service;

    @BeforeEach
    void setUp() {
        service = new GlossaryApplicationService(userPort, translationCacheAdminPort, cacheVersionPort);
    }

    @Test
    @DisplayName("createGlossaryItem 创建后失效术语缓存")
    void createGlossaryItem_invalidatesCache() {
        GlossaryItemRequest request = new GlossaryItemRequest();
        request.setSourceWord("Apple");
        request.setTargetWord("苹果");

        GlossaryResponse resp = new GlossaryResponse();
        resp.setId(1L);
        when(userPort.createGlossaryItem(anyLong(), any())).thenReturn(resp);

        GlossaryResponse result = service.createGlossaryItem(1L, request);

        assertNotNull(result);
        verify(translationCacheAdminPort).invalidateKeysForTerm("Apple");
    }

    @Test
    @DisplayName("updateGlossaryItem 更新后失效术语缓存")
    void updateGlossaryItem_invalidatesCache() {
        GlossaryItemRequest request = new GlossaryItemRequest();
        request.setSourceWord("Banana");
        request.setTargetWord("香蕉");

        GlossaryResponse resp = new GlossaryResponse();
        resp.setId(1L);
        when(userPort.updateGlossaryItem(anyLong(), anyLong(), any())).thenReturn(resp);

        GlossaryResponse result = service.updateGlossaryItem(1L, 1L, request);

        assertNotNull(result);
        verify(translationCacheAdminPort).invalidateKeysForTerm("Banana");
    }

    @Test
    @DisplayName("updateGlossaryItem 返回null时不失效缓存")
    void updateGlossaryItem_nullResponse_noCacheInvalidation() {
        GlossaryItemRequest request = new GlossaryItemRequest();
        request.setSourceWord("Banana");

        when(userPort.updateGlossaryItem(anyLong(), anyLong(), any())).thenReturn(null);

        GlossaryResponse result = service.updateGlossaryItem(1L, 1L, request);

        assertNull(result);
        verifyNoInteractions(translationCacheAdminPort);
    }

    @Test
    @DisplayName("deleteGlossaryItem 删除成功后 bump 版本号")
    void deleteGlossaryItem_bumpsVersionOnSuccess() {
        when(userPort.deleteGlossaryItem(anyLong(), anyLong())).thenReturn(true);

        boolean result = service.deleteGlossaryItem(1L, 1L);

        assertTrue(result);
        verify(cacheVersionPort).bumpAllVersions();
    }

    @Test
    @DisplayName("deleteGlossaryItem 删除失败不 bump 版本号")
    void deleteGlossaryItem_noBumpOnFailure() {
        when(userPort.deleteGlossaryItem(anyLong(), anyLong())).thenReturn(false);

        boolean result = service.deleteGlossaryItem(1L, 1L);

        assertFalse(result);
        verifyNoInteractions(cacheVersionPort);
    }

    @Test
    @DisplayName("importGlossaryCsv 批量导入后失效所有术语缓存")
    void importGlossaryCsv_invalidatesAllTerms() {
        GlossaryItemRequest item1 = new GlossaryItemRequest();
        item1.setSourceWord("Apple");
        item1.setTargetWord("苹果");
        GlossaryItemRequest item2 = new GlossaryItemRequest();
        item2.setSourceWord("Banana");
        item2.setTargetWord("香蕉");
        GlossaryItemRequest item3 = new GlossaryItemRequest();
        item3.setSourceWord("Cherry");
        item3.setTargetWord("樱桃");
        List<GlossaryItemRequest> items = List.of(item1, item2, item3);

        when(userPort.batchImportGlossaryItems(anyLong(), anyList())).thenReturn(3);

        int count = service.importGlossaryCsv(1L, items);

        assertEquals(3, count);
        verify(translationCacheAdminPort).invalidateKeysForTerm("Apple");
        verify(translationCacheAdminPort).invalidateKeysForTerm("Banana");
        verify(translationCacheAdminPort).invalidateKeysForTerm("Cherry");
    }

    @Test
    @DisplayName("importGlossaryCsv 跳过空 sourceWord")
    void importGlossaryCsv_skipsEmptySourceWord() {
        GlossaryItemRequest item1 = new GlossaryItemRequest();
        item1.setSourceWord("");
        item1.setTargetWord("苹果");
        GlossaryItemRequest item2 = new GlossaryItemRequest();
        item2.setSourceWord(null);
        item2.setTargetWord("香蕉");
        List<GlossaryItemRequest> items = List.of(item1, item2);

        when(userPort.batchImportGlossaryItems(anyLong(), anyList())).thenReturn(0);

        service.importGlossaryCsv(1L, items);

        verifyNoInteractions(translationCacheAdminPort);
    }

    @Test
    @DisplayName("listGlossaries 委托给 userPort")
    void listGlossaries_delegatesToUserPort() {
        PageResponse<GlossaryResponse> page = new PageResponse<>();
        page.setList(List.of());
        page.setTotal(0L);
        when(userPort.getGlossaryList(anyLong(), anyInt(), anyInt(), anyString())).thenReturn(page);

        PageResponse<GlossaryResponse> result = service.listGlossaries(1L, 1, 10, "apple");

        assertNotNull(result);
        verify(userPort).getGlossaryList(1L, 1, 10, "apple");
    }

    @Test
    @DisplayName("getGlossaryDetail 委托给 userPort")
    void getGlossaryDetail_delegatesToUserPort() {
        GlossaryResponse resp = new GlossaryResponse();
        resp.setId(1L);
        when(userPort.getGlossaryDetail(anyLong(), anyLong())).thenReturn(resp);

        GlossaryResponse result = service.getGlossaryDetail(1L, 1L);

        assertNotNull(result);
        verify(userPort).getGlossaryDetail(1L, 1L);
    }

    @Test
    @DisplayName("getAllGlossaryTerms 委托给 userPort")
    void getAllGlossaryTerms_delegatesToUserPort() {
        when(userPort.getGlossaryTerms(anyLong())).thenReturn(List.of());

        List<GlossaryResponse> result = service.getAllGlossaryTerms(1L);

        assertNotNull(result);
        verify(userPort).getGlossaryTerms(1L);
    }
}
