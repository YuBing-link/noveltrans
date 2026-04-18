package com.yumu.noveltranslator.controller.web;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.Document;
import com.yumu.noveltranslator.entity.TranslationTask;
import com.yumu.noveltranslator.service.DocumentService;
import com.yumu.noveltranslator.service.TranslationTaskService;
import com.yumu.noveltranslator.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Web 文档管理接口
 * 路径前缀: /user/documents
 */
@RestController
@RequestMapping("/user/documents")
@RequiredArgsConstructor
public class WebDocumentController {

    private final DocumentService documentService;
    private final TranslationTaskService translationTaskService;

    /**
     * 获取文档列表
     * GET /user/documents
     */
    @GetMapping
    public Result<PageResponse<DocumentInfoResponse>> getDocuments(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(required = false, defaultValue = "all") String status) {

        Long userId = SecurityUtil.getRequiredUserId();

        List<Document> documents = documentService.getUserDocuments(userId, status);
        List<DocumentInfoResponse> responseList = documents.stream()
                .map(documentService::toDocumentInfoResponse)
                .toList();

        int total = responseList.size();
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);
        if (fromIndex < total) {
            responseList = responseList.subList(fromIndex, toIndex);
        }

        PageResponse<DocumentInfoResponse> response = PageResponse.of(page, pageSize, (long) total, responseList);
        return Result.ok(response);
    }

    /**
     * 获取文档详情
     * GET /user/documents/{docId}
     */
    @GetMapping("/{docId}")
    public Result<DocumentInfoResponse> getDocument(@PathVariable Long docId) {
        Long userId = SecurityUtil.getRequiredUserId();

        Document doc = documentService.getDocumentById(docId, userId);
        if (doc == null) {
            return Result.error("文档不存在");
        }

        return Result.ok(documentService.toDocumentInfoResponse(doc));
    }

    /**
     * 删除文档
     * DELETE /user/documents/{docId}
     */
    @DeleteMapping("/{docId}")
    public Result<Void> deleteDocument(@PathVariable Long docId) {
        Long userId = SecurityUtil.getRequiredUserId();

        if (documentService.deleteDocument(docId, userId)) {
            return Result.ok(null);
        } else {
            return Result.error("删除失败");
        }
    }

    /**
     * 重新翻译
     * POST /user/documents/{docId}/retry
     */
    @PostMapping("/{docId}/retry")
    public Result<Void> retryTranslation(@PathVariable Long docId) {
        Long userId = SecurityUtil.getRequiredUserId();

        if (documentService.retryTranslation(docId, userId)) {
            return Result.ok(null);
        } else {
            return Result.error("重新翻译失败");
        }
    }

    /**
     * 开始翻译（上传后用户点击触发）
     * POST /user/documents/{docId}/start
     */
    @PostMapping("/{docId}/start")
    public Result<TaskStatusResponse> startTranslation(@PathVariable Long docId) {
        Long userId = SecurityUtil.getRequiredUserId();

        Document doc = documentService.getDocumentById(docId, userId);
        if (doc == null) {
            return Result.error("文档不存在");
        }

        TranslationTask task = translationTaskService.getTaskByDocumentId(docId);
        if (task == null) {
            return Result.error("翻译任务不存在");
        }

        translationTaskService.startDocumentTranslation(task, doc);
        return Result.ok(translationTaskService.toTaskStatusResponse(task));
    }

    /**
     * 上传文档（不自动翻译）
     * POST /user/documents/upload
     */
    @PostMapping("/upload")
    public Result<DocumentTranslationResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sourceLang", defaultValue = "auto") String sourceLang,
            @RequestParam(value = "targetLang") String targetLang,
            @RequestParam(value = "mode", defaultValue = "novel") String mode) {

        Long userId = SecurityUtil.getRequiredUserId();

        try {
            DocumentTranslationRequest request = new DocumentTranslationRequest();
            request.setSourceLang(sourceLang);
            request.setTargetLang(targetLang);
            request.setMode(mode);

            Document doc = documentService.uploadDocument(userId, file, request);
            TranslationTask task = translationTaskService.createDocumentTask(userId, doc);

            DocumentTranslationResponse response = new DocumentTranslationResponse();
            response.setTaskId(task.getTaskId());
            response.setDocumentId(doc.getId());
            response.setDocumentName(doc.getName());
            response.setStatus(task.getStatus());
            response.setMessage("文档上传成功，请点击开始翻译");

            return Result.ok(response);

        } catch (IOException e) {
            return Result.error("文件上传失败：" + e.getMessage());
        }
    }

    /**
     * 下载翻译后的文档
     * GET /user/documents/{docId}/download
     */
    @GetMapping("/{docId}/download")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long docId) {
        Long userId = SecurityUtil.getRequiredUserId();

        Document doc = documentService.getDocumentById(docId, userId);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            Path UPLOAD_BASE = Paths.get("uploads/documents/").normalize().toAbsolutePath();
            Path targetPath = Paths.get(doc.getPath()).normalize().toAbsolutePath();
            if (!targetPath.startsWith(UPLOAD_BASE)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            byte[] fileContent = Files.readAllBytes(targetPath);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", doc.getName());

            return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
