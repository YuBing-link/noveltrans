package com.yumu.noveltranslator.controller.shared;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.Document;
import com.yumu.noveltranslator.entity.TranslationTask;
import com.yumu.noveltranslator.service.DocumentService;
import com.yumu.noveltranslator.service.RagTranslationService;
import com.yumu.noveltranslator.service.TranslationService;
import com.yumu.noveltranslator.service.TranslationTaskService;
import com.yumu.noveltranslator.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 共享翻译接口（Web 和插件共用）
 * 路径前缀: /v1/translate
 */
@RestController
@RequestMapping("/v1/translate")
@RequiredArgsConstructor
public class SharedTranslateController {

    private final TranslationService translationService;
    private final TranslationTaskService translationTaskService;
    private final DocumentService documentService;
    private final RagTranslationService ragTranslationService;

    /**
     * 文本翻译
     * POST /v1/translate/text
     */
    @PostMapping("/text")
    public Result<TextTranslationResponse> translateText(@RequestBody @Valid TextTranslationRequest request) {
        try {
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

            return Result.ok(response);
        } catch (Exception e) {
            return Result.error("翻译失败：" + e.getMessage());
        }
    }

    /**
     * 查询翻译任务状态
     * GET /v1/translate/task/{taskId}
     */
    @GetMapping("/task/{taskId}")
    public Result<TaskStatusResponse> getTaskStatus(@PathVariable String taskId) {
        TranslationTask task = translationTaskService.getTaskByTaskId(taskId);
        if (task == null) {
            return Result.error("任务不存在");
        }

        return Result.ok(translationTaskService.toTaskStatusResponse(task));
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
            return Result.ok(null);
        } else {
            return Result.error("取消失败，任务可能已完成或正在处理");
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
            return Result.error("任务不存在或结果不可用");
        }

        return Result.ok(result);
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

    /**
     * RAG 翻译记忆查询
     * POST /v1/translate/rag
     */
    @PostMapping("/rag")
    public Result<RagTranslationResponse> queryRag(@RequestBody @Valid RagTranslationRequest request) {
        RagTranslationResponse response = ragTranslationService.searchSimilar(
                request.getText(), request.getTargetLang(), request.getEngine());
        return Result.ok(response);
    }
}
