package com.yumu.noveltranslator.controller.web;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.Document;
import com.yumu.noveltranslator.entity.TranslationTask;
import com.yumu.noveltranslator.enums.TranslationStatus;
import com.yumu.noveltranslator.service.CollabProjectService;
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
    private final CollabProjectService collabProjectService;

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
     * 取消翻译
     * POST /user/documents/{docId}/cancel
     */
    @PostMapping("/{docId}/cancel")
    public Result<Void> cancelTranslation(@PathVariable Long docId) {
        Long userId = SecurityUtil.getRequiredUserId();

        TranslationTask task = translationTaskService.getTaskByDocumentId(docId);
        if (task == null) {
            return Result.error("翻译任务不存在");
        }
        if (!task.getUserId().equals(userId)) {
            return Result.error("无权操作");
        }
        if (translationTaskService.cancelTask(task.getTaskId(), userId)) {
            return Result.ok(null);
        } else {
            return Result.error("取消失败，任务可能已完成或正在处理");
        }
    }

    /**
     * 重新翻译
     * POST /user/documents/{docId}/retry
     */
    @PostMapping("/{docId}/retry")
    public Result<Void> retryDocument(@PathVariable Long docId) {
        Long userId = SecurityUtil.getRequiredUserId();

        if (documentService.retryTranslation(docId, userId)) {
            return Result.ok(null);
        } else {
            return Result.error("重试失败，文档不存在");
        }
    }

    /**
     * 上传文档（自动创建翻译任务，后续由前端触发SSE流式翻译）
     * POST /user/documents/upload
     */
    @PostMapping("/upload")
    public Result<DocumentTranslationResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sourceLang", defaultValue = "auto") String sourceLang,
            @RequestParam(value = "targetLang") String targetLang,
            @RequestParam(value = "mode", defaultValue = "fast") String mode) {

        Long userId = SecurityUtil.getRequiredUserId();

        try {
            DocumentTranslationRequest request = new DocumentTranslationRequest();
            request.setSourceLang(sourceLang);
            request.setTargetLang(targetLang);
            request.setMode(mode);

            Document doc = documentService.uploadDocument(userId, file, request);

            // 团队模式：自动创建协作项目并拆分章节
            if ("team".equals(mode)) {
                CollabProjectService.TeamProjectCreateResult result =
                        collabProjectService.createProjectFromDocument(
                                userId, doc.getId(), doc.getName(), doc.getPath(), doc.getFileType(),
                                sourceLang, targetLang);

                // 事务已提交，启动多 Agent 翻译
                collabProjectService.startMultiAgentTranslation(result.projectId());

                DocumentTranslationResponse response = new DocumentTranslationResponse();
                response.setTaskId(null);
                response.setDocumentId(doc.getId());
                response.setDocumentName(doc.getName());
                response.setStatus(TranslationStatus.PENDING.getValue());
                response.setProjectId(result.projectId());
                response.setMessage("团队模式已创建项目，共 " + result.chapterCount() + " 个章节");
                return Result.ok(response);
            }

            // fast/expert 模式：创建任务后直接异步启动翻译
            TranslationTask task = translationTaskService.createDocumentTask(userId, doc);
            translationTaskService.startDocumentTranslation(task, doc);

            DocumentTranslationResponse response = new DocumentTranslationResponse();
            response.setTaskId(task.getTaskId());
            response.setDocumentId(doc.getId());
            response.setDocumentName(doc.getName());
            response.setStatus(task.getStatus());
            response.setProjectId(null);
            response.setMessage("文档上传成功");

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
