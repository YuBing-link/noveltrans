package com.yumu.noveltranslator.controller.external;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.Document;
import com.yumu.noveltranslator.entity.TranslationTask;
import com.yumu.noveltranslator.service.DocumentService;
import com.yumu.noveltranslator.service.QuotaService;
import com.yumu.noveltranslator.service.TranslationService;
import com.yumu.noveltranslator.service.TranslationTaskService;
import com.yumu.noveltranslator.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 外部 API 端（API Key 认证，类似 OpenAI API 调用方式）
 * 路径前缀: /v1/external
 * 认证方式: Authorization: Bearer nt_sk_xxxx
 */
@RestController
@RequestMapping("/v1/external")
@RequiredArgsConstructor
@Slf4j
public class ExternalTranslateController {

    private final TranslationService translationService;
    private final DocumentService documentService;
    private final TranslationTaskService translationTaskService;
    private final QuotaService quotaService;

    @Value("${translation.external.max-chars:5000}")
    private int maxCharsPerRequest;

    /**
     * 文本翻译（类似 OpenAI /v1/chat/completions 风格）
     * POST /v1/external/translate
     */
    @PostMapping("/translate")
    public Result<ExternalTranslateResponse> translate(@RequestBody @Valid ExternalTranslateRequest request) {
        Long userId = resolveUserId();
        if (userId == null) {
            return Result.error("认证失败");
        }

        if (request.getText() == null || request.getText().isBlank()) {
            return Result.error("文本不能为空");
        }

        if (request.getText().length() > maxCharsPerRequest) {
            return Result.error("文本超过限制（最大 " + maxCharsPerRequest + " 字符）");
        }

        try {
            SelectionTranslationRequest selectionReq = new SelectionTranslationRequest(
                request.getText(),
                request.getSourceLang() != null ? request.getSourceLang() : "auto",
                request.getTargetLang(),
                request.getEngine() != null ? request.getEngine() : "google",
                "fast"
            );

            String result = translationService.selectionTranslate(selectionReq).getTranslation();

            ExternalTranslateResponse response = new ExternalTranslateResponse();
            response.setTranslatedText(result);
            response.setSourceLang(selectionReq.getSourceLang());
            response.setTargetLang(selectionReq.getTargetLang());
            response.setEngine(selectionReq.getEngine());
            response.setUsage(result.length());

            return Result.ok(response);
        } catch (Exception e) {
            log.error("外部 API 翻译失败", e);
            return Result.error("翻译失败：" + e.getMessage());
        }
    }

    /**
     * 批量文本翻译
     * POST /v1/external/batch
     */
    @PostMapping("/batch")
    public Result<List<ExternalTranslateResponse>> batchTranslate(@RequestBody @Valid ExternalBatchTranslateRequest request) {
        Long userId = resolveUserId();
        if (userId == null) {
            return Result.error("认证失败");
        }

        if (request.getTexts() == null || request.getTexts().isEmpty()) {
            return Result.error("文本列表不能为空");
        }

        if (request.getTexts().size() > 50) {
            return Result.error("批量翻译最多支持 50 条文本");
        }

        // 简单循环翻译
        var results = request.getTexts().stream().map(text -> {
            try {
                SelectionTranslationRequest selectionReq = new SelectionTranslationRequest(
                    text,
                    request.getSourceLang() != null ? request.getSourceLang() : "auto",
                    request.getTargetLang(),
                    request.getEngine() != null ? request.getEngine() : "google",
                    "fast"
                );
                String translated = translationService.selectionTranslate(selectionReq).getTranslation();
                ExternalTranslateResponse resp = new ExternalTranslateResponse();
                resp.setTranslatedText(translated);
                resp.setSourceLang(selectionReq.getSourceLang());
                resp.setTargetLang(selectionReq.getTargetLang());
                resp.setEngine(selectionReq.getEngine());
                resp.setUsage(translated.length());
                return resp;
            } catch (Exception e) {
                log.warn("批量翻译单条失败: {}", e.getMessage());
                ExternalTranslateResponse resp = new ExternalTranslateResponse();
                resp.setError(e.getMessage());
                return resp;
            }
        }).toList();

        return Result.ok(results);
    }

    /**
     * 获取可用翻译引擎列表
     * GET /v1/external/models
     */
    @GetMapping("/models")
    public Result<List<Map<String, Object>>> getModels() {
        List<Map<String, Object>> models = List.of(
            Map.of("id", "google", "name", "Google Translate", "type", "free"),
            Map.of("id", "mymemory", "name", "MyMemory", "type", "free"),
            Map.of("id", "libre", "name", "LibreTranslate", "type", "free"),
            Map.of("id", "baidu", "name", "Baidu Translate", "type", "api_key"),
            Map.of("id", "deepl", "name", "DeepL", "type", "api_key"),
            Map.of("id", "openai", "name", "OpenAI", "type", "api_key")
        );
        return Result.ok(models);
    }

    /**
     * 下载翻译结果（通过任务 ID）
     * GET /v1/external/task/{taskId}/download
     */
    @GetMapping("/task/{taskId}/download")
    public ResponseEntity<byte[]> downloadTranslation(@PathVariable String taskId) {
        Long userId = resolveUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

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

    private Long resolveUserId() {
        // 从 SecurityContext 中获取用户 ID（通过 API Key 认证或 JWT 认证）
        try {
            return SecurityUtil.getRequiredUserId();
        } catch (Exception e) {
            return null;
        }
    }
}
