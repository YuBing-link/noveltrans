package com.yumu.noveltranslator.controller;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.Document;
import com.yumu.noveltranslator.entity.TranslationTask;
import com.yumu.noveltranslator.service.DocumentService;
import com.yumu.noveltranslator.service.RagTranslationService;
import com.yumu.noveltranslator.service.TranslationTaskService;
import com.yumu.noveltranslator.service.TranslationService;
import com.yumu.noveltranslator.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 翻译控制器
 */
@RestController
@RequestMapping("/v1/translate")
public class TranslateController {

    @Autowired
    private TranslationService translationService;

    @Autowired
    private TranslationTaskService translationTaskService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private RagTranslationService ragTranslationService;

    // ==================== 文本翻译 ====================

    /**
     * 文本翻译
     * POST /v1/translate/text
     */
    @PostMapping("/text")
    public Result<TextTranslationResponse> translateText(@RequestBody @Valid TextTranslationRequest request) {
        try {
            // 调用翻译服务
            SelectionTranslationRequest selectionReq = new SelectionTranslationRequest(
                request.getText(),
                request.getSourceLang(),
                request.getTargetLang(),
                request.getEngine(),
                null,
                "fast"
            );

            String result = translationService.selectionTranslate(selectionReq).getTranslation();

            TextTranslationResponse response = new TextTranslationResponse();
            response.setTranslatedText(result);
            response.setTargetLang(request.getTargetLang());
            response.setDetectedLang(request.getSourceLang());
            response.setEngine(request.getEngine() != null ? request.getEngine() : "auto");

            return Result.ok(response, "200");
        } catch (Exception e) {
            return Result.error("翻译失败：" + e.getMessage(), "500");
        }
    }

    // ==================== 文档翻译 ====================

    /**
     * 文档翻译
     * POST /v1/translate/document
     */
    @PostMapping("/document")
    @PreAuthorize("isAuthenticated()")
    public Result<DocumentTranslationResponse> translateDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sourceLang", defaultValue = "auto") String sourceLang,
            @RequestParam(value = "targetLang") String targetLang,
            @RequestParam(value = "mode", defaultValue = "fast") String mode) {

        Long userId = SecurityUtil.getRequiredUserId();

        try {
            DocumentTranslationRequest request = new DocumentTranslationRequest();
            request.setSourceLang(sourceLang);
            request.setTargetLang(targetLang);

            String engine;
            String translateMode;
            switch (mode) {
                case "fast" -> {
                    engine = "mtran";
                    translateMode = "literal";
                }
                case "expert" -> {
                    engine = "openai";
                    translateMode = "novel";
                }
                case "team" -> {
                    engine = "collab";
                    translateMode = "free";
                }
                default -> {
                    engine = "google";
                    translateMode = "novel";
                }
            }
            request.setEngine(engine);
            request.setMode(translateMode);

            Document doc = documentService.uploadDocument(userId, file, request);
            TranslationTask task = translationTaskService.createDocumentTask(userId, doc);

            DocumentTranslationResponse response = new DocumentTranslationResponse();
            response.setTaskId(task.getTaskId());
            response.setDocumentId(doc.getId());
            response.setDocumentName(doc.getName());
            response.setStatus(task.getStatus());
            response.setMessage("文档上传成功，开始翻译");

            return Result.ok(response, "200");
        } catch (IOException e) {
            return Result.error("文件上传失败：" + e.getMessage(), "500");
        }
    }

    // ==================== 任务管理 ====================

    /**
     * 查询翻译任务状态
     * GET /v1/translate/task/{taskId}
     */
    @GetMapping("/task/{taskId}")
    public Result<TaskStatusResponse> getTaskStatus(@PathVariable String taskId) {
        TranslationTask task = translationTaskService.getTaskByTaskId(taskId);
        if (task == null) {
            return Result.error("任务不存在", "404");
        }

        return Result.ok(translationTaskService.toTaskStatusResponse(task), "200");
    }

    /**
     * 取消翻译任务
     * DELETE /v1/translate/task/{taskId}
     */
    @DeleteMapping("/task/{taskId}")
    @PreAuthorize("isAuthenticated()")
    public Result cancelTask(@PathVariable String taskId) {
        Long userId = SecurityUtil.getRequiredUserId();

        if (translationTaskService.cancelTask(taskId, userId)) {
            return Result.ok(null, "200");
        } else {
            return Result.error("取消失败，任务可能已完成或正在处理", "400");
        }
    }

    /**
     * 获取翻译结果
     * GET /v1/translate/task/{taskId}/result
     */
    @GetMapping("/task/{taskId}/result")
    public Result<TranslationResultResponse> getTranslationResult(@PathVariable String taskId) {
        TranslationResultResponse result = translationTaskService.getTranslationResult(taskId);
        if (result == null) {
            return Result.error("任务不存在或结果不可用", "404");
        }

        return Result.ok(result, "200");
    }

    /**
     * 下载翻译结果
     * GET /v1/translate/task/{taskId}/download
     */
    @GetMapping("/task/{taskId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadTranslation(@PathVariable String taskId) {
        Long userId = SecurityUtil.getRequiredUserId();

        String filePath = translationTaskService.getDownloadPath(taskId, userId);
        if (filePath == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            byte[] fileContent = Files.readAllBytes(Paths.get(filePath));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "translated_" + taskId);

            return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== 原有接口（浏览器扩展用）====================

    /**
     * Selection translation - 允许公共访问，但认证用户享有更高限制
     * POST /v1/translate/selection
     */
    @PostMapping(value = "/selection")
    public SelectionTranslateResponse translateSelection(@RequestBody @Valid SelectionTranslationRequest req) {
        return translationService.selectionTranslate(req);
    }

    /**
     * Reader translation - 允许公共访问，但认证用户享有更高限制
     * POST /v1/translate/reader
     */
    @PostMapping(value = "/reader")
    public ReaderTranslateResponse translateReader(@RequestBody @Valid ReaderTranslateRequest req) {
        return translationService.readerTranslate(req);
    }

    /**
     * Webpage translation - 允许公共访问，但认证用户享有更高限制
     * POST /v1/translate/webpage
     */
    @PostMapping(value = "/webpage", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter translateWebpage(@RequestBody @Valid WebpageTranslateRequest req) {
        return translationService.webpageTranslateStream(req);
    }

    /**
     * 为认证用户提供高级翻译功能
     * POST /v1/translate/premium-selection
     */
    @PostMapping(value = "/premium-selection")
    @PreAuthorize("isAuthenticated()")
    public SelectionTranslateResponse premiumTranslateSelection(@RequestBody @Valid SelectionTranslationRequest req) {
        return translationService.selectionTranslate(req);
    }

    @PostMapping(value = "/premium-reader")
    @PreAuthorize("isAuthenticated()")
    public ReaderTranslateResponse premiumTranslateReader(@RequestBody @Valid ReaderTranslateRequest req) {
        return translationService.readerTranslate(req);
    }

    // ==================== RAG 翻译记忆查询 ====================

    /**
     * RAG 翻译记忆查询
     * POST /v1/translate/rag
     * 查询相似翻译记忆，返回直接命中或参考译文
     */
    @PostMapping("/rag")
    public Result<RagTranslationResponse> queryRag(@RequestBody @Valid RagTranslationRequest request) {
        RagTranslationResponse response = ragTranslationService.searchSimilar(
                request.getText(), request.getTargetLang(), request.getEngine());
        return Result.ok(response, "200");
    }
}
