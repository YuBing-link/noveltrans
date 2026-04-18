package com.yumu.noveltranslator.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DtoTest {

    @Test
    void loginRequest() {
        LoginRequest req = new LoginRequest();
        req.setEmail("test@example.com");
        req.setPassword("password123");
        assertEquals("test@example.com", req.getEmail());
        assertEquals("password123", req.getPassword());
    }

    @Test
    void registerRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("test@example.com");
        req.setPassword("password123");
        req.setCode("123456");
        req.setUsername("testuser");
        req.setAvatar("https://example.com/avatar.png");
        assertEquals("test@example.com", req.getEmail());
        assertEquals("123456", req.getCode());
    }

    @Test
    void webpageTranslateRequest() {
        WebpageTranslateRequest req = new WebpageTranslateRequest();
        req.setTargetLang("zh");
        req.setEngine("google");
        assertEquals("zh", req.getTargetLang());
        assertNull(req.getTextRegistry());
    }

    @Test
    void webpageTranslateResponse() {
        WebpageTranslateResponse resp = new WebpageTranslateResponse(true, "google", java.util.List.of(
                new WebpageTranslateResponse.Translation("text-001", "Hello", "你好")
        ));
        assertTrue(resp.getSuccess());
        assertEquals("google", resp.getEngine());
    }

    @Test
    void webpageTranslateResponseTranslationInnerClass() {
        WebpageTranslateResponse.Translation t = new WebpageTranslateResponse.Translation("t1", "World", "世界");
        assertEquals("World", t.getOriginal());
        assertEquals("世界", t.getTranslation());
    }

    @Test
    void selectionTranslateResponse() {
        SelectionTranslateResponse resp = new SelectionTranslateResponse(true, "google", "hello");
        assertTrue(resp.getSuccess());
        assertEquals("google", resp.getEngine());
        assertEquals("hello", resp.getTranslation());
    }

    @Test
    void readerTranslateResponse() {
        ReaderTranslateResponse resp = new ReaderTranslateResponse(true, "google", "翻译结果");
        assertTrue(resp.getSuccess());
        assertEquals("google", resp.getEngine());
        assertEquals("翻译结果", resp.getTranslatedContent());
    }

    @Test
    void textTranslationRequest() {
        TextTranslationRequest req = new TextTranslationRequest();
        req.setText("Hello World");
        req.setSourceLang("en");
        req.setTargetLang("zh");
        req.setEngine("google");
        assertEquals("Hello World", req.getText());
    }

    @Test
    void textTranslationResponse() {
        TextTranslationResponse resp = new TextTranslationResponse();
        resp.setTranslatedText("你好世界");
        resp.setTargetLang("zh");
        resp.setDetectedLang("en");
        resp.setEngine("google");
        assertEquals("你好世界", resp.getTranslatedText());
    }

    @Test
    void documentTranslationRequest() {
        DocumentTranslationRequest req = new DocumentTranslationRequest();
        req.setSourceLang("en");
        req.setTargetLang("zh");
        req.setMode("novel");
        assertEquals("en", req.getSourceLang());
    }

    @Test
    void documentTranslationResponse() {
        DocumentTranslationResponse resp = new DocumentTranslationResponse();
        resp.setTaskId("task-001");
        resp.setStatus("processing");
        resp.setDocumentName("test.txt");
        assertEquals("task-001", resp.getTaskId());
    }

    @Test
    void taskStatusResponse() {
        TaskStatusResponse resp = new TaskStatusResponse();
        resp.setTaskId("task-001");
        resp.setStatus("completed");
        resp.setProgress(100);
        assertEquals(100, resp.getProgress());
    }

    @Test
    void translationResultResponse() {
        TranslationResultResponse resp = new TranslationResultResponse();
        resp.setTranslatedText("翻译结果");
        resp.setTranslatedFilePath("/path/to/file");
        resp.setStatus("completed");
        assertEquals("翻译结果", resp.getTranslatedText());
    }

    @Test
    void sendCodeRequest() {
        SendCodeRequest req = new SendCodeRequest();
        req.setEmail("test@example.com");
        assertEquals("test@example.com", req.getEmail());
    }

    @Test
    void changePasswordRequest() {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("old");
        req.setNewPassword("new");
        assertEquals("old", req.getOldPassword());
    }

    @Test
    void resetPasswordRequest() {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setEmail("test@example.com");
        req.setCode("123456");
        req.setNewPassword("newpass");
        assertEquals("test@example.com", req.getEmail());
    }

    @Test
    void refreshTokenRequest() {
        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken("jwt-token");
        assertEquals("jwt-token", req.getRefreshToken());
    }

    @Test
    void updateUserProfileRequest() {
        UpdateUserProfileRequest req = new UpdateUserProfileRequest();
        req.setUsername("newname");
        req.setAvatar("https://example.com/new.png");
        assertEquals("newname", req.getUsername());
    }

    @Test
    void glossaryItemRequest() {
        GlossaryItemRequest req = new GlossaryItemRequest();
        req.setSourceWord("hello");
        req.setTargetWord("你好");
        req.setRemark("greeting");
        assertEquals("hello", req.getSourceWord());
    }

    @Test
    void glossaryResponse() {
        GlossaryResponse resp = new GlossaryResponse();
        resp.setId(1L);
        resp.setSourceWord("hello");
        resp.setTargetWord("你好");
        resp.setRemark("greeting");
        assertEquals(1L, resp.getId());
    }

    @Test
    void glossaryTermResponse() {
        GlossaryTermResponse resp = new GlossaryTermResponse();
        resp.setId(1L);
        resp.setSourceWord("hello");
        resp.setTargetWord("你好");
        assertEquals("hello", resp.getSourceWord());
    }

    @Test
    void userPreferencesRequest() {
        UserPreferencesRequest req = new UserPreferencesRequest();
        req.setDefaultEngine("deepl");
        req.setDefaultTargetLang("en");
        req.setEnableGlossary(true);
        req.setFontSize(16);
        req.setThemeMode("dark");
        assertEquals("deepl", req.getDefaultEngine());
    }

    @Test
    void userPreferencesResponse() {
        UserPreferencesResponse resp = new UserPreferencesResponse();
        resp.setDefaultEngine("google");
        resp.setDefaultTargetLang("zh");
        resp.setEnableGlossary(true);
        resp.setFontSize(14);
        resp.setThemeMode("light");
        assertEquals("google", resp.getDefaultEngine());
    }

    @Test
    void userStatisticsResponse() {
        UserStatisticsResponse resp = new UserStatisticsResponse();
        resp.setTotalTranslations(100);
        resp.setTextTranslations(80);
        resp.setDocumentTranslations(20);
        resp.setTotalCharacters(50000L);
        assertEquals(100, resp.getTotalTranslations());
    }

    @Test
    void userQuotaResponse() {
        UserQuotaResponse resp = new UserQuotaResponse();
        resp.setUserLevel("free");
        resp.setDailyLimit(100);
        resp.setUsedToday(10);
        resp.setRemaining(90);
        assertEquals(100, resp.getDailyLimit());
    }

    @Test
    void translationHistoryResponse() {
        TranslationHistoryResponse resp = new TranslationHistoryResponse();
        resp.setId(1L);
        resp.setTaskId("task-001");
        resp.setSourceLang("en");
        resp.setTargetLang("zh");
        resp.setSourceTextPreview("Hello");
        resp.setTargetTextPreview("你好");
        assertEquals("Hello", resp.getSourceTextPreview());
    }

    @Test
    void platformStatsResponse() {
        PlatformStatsResponse resp = new PlatformStatsResponse();
        resp.setTotalUsers(100);
        resp.setActiveUsersToday(50);
        resp.setTotalTranslations(1000L);
        resp.setSystemStatus("normal");
        assertEquals(100, resp.getTotalUsers());
    }

    @Test
    void pageRequest() {
        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPageSize(10);
        assertEquals(1, req.getPage());
        assertEquals(10, req.getPageSize());
    }

    @Test
    void pageResponse() {
        PageResponse<String> resp = new PageResponse<>();
        resp.setTotal(100L);
        resp.setPage(1);
        resp.setPageSize(10);
        resp.setList(java.util.List.of("item1", "item2"));
        assertEquals(100L, resp.getTotal());
    }

    @Test
    void translationEvent() {
        TranslationEvent event = new TranslationEvent();
        event.setType("delta");
        event.setContent("翻译结果");
        event.setExtra(null);
        assertEquals("delta", event.getType());
        assertEquals("翻译结果", event.getContent());
    }

    @Test
    void readerTranslateRequest() {
        ReaderTranslateRequest req = new ReaderTranslateRequest();
        req.setContent("<p>Hello</p>");
        req.setSourceLang("en");
        req.setTargetLang("zh");
        req.setEngine("google");
        assertEquals("<p>Hello</p>", req.getContent());
    }
}
