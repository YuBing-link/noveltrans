package com.yumu.noveltranslator.controller;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.Document;
import com.yumu.noveltranslator.entity.TranslationTask;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.service.DocumentService;
import com.yumu.noveltranslator.service.QuotaService;
import com.yumu.noveltranslator.service.TranslationTaskService;
import com.yumu.noveltranslator.mapper.UserMapper;
import com.yumu.noveltranslator.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档管理控制器
 */
@RestController
@RequestMapping("/user/documents")
@PreAuthorize("isAuthenticated()")
public class DocumentController {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private TranslationTaskService translationTaskService;

    @Autowired
    private QuotaService quotaService;

    @Autowired
    private UserMapper userMapper;

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

        // 计算分页
        int total = responseList.size();
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);
        if (fromIndex < total) {
            responseList = responseList.subList(fromIndex, toIndex);
        }

        PageResponse<DocumentInfoResponse> response = PageResponse.of(page, pageSize, (long) total, responseList);
        return Result.ok(response, "200");
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
            return Result.error("文档不存在", "404");
        }

        return Result.ok(documentService.toDocumentInfoResponse(doc), "200");
    }

    /**
     * 删除文档
     * DELETE /user/documents/{docId}
     */
    @DeleteMapping("/{docId}")
    public Result deleteDocument(@PathVariable Long docId) {
        Long userId = SecurityUtil.getRequiredUserId();

        if (documentService.deleteDocument(docId, userId)) {
            return Result.ok(null, "200");
        } else {
            return Result.error("删除失败", "500");
        }
    }

    /**
     * 重新翻译
     * POST /user/documents/{docId}/retry
     */
    @PostMapping("/{docId}/retry")
    public Result retryTranslation(@PathVariable Long docId) {
        Long userId = SecurityUtil.getRequiredUserId();

        if (documentService.retryTranslation(docId, userId)) {
            return Result.ok(null, "200");
        } else {
            return Result.error("重新翻译失败", "500");
        }
    }

    /**
     * 上传并翻译文档
     * POST /user/documents/upload
     */
    @PostMapping("/upload")
    public Result<DocumentTranslationResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sourceLang", defaultValue = "auto") String sourceLang,
            @RequestParam(value = "targetLang") String targetLang,
            @RequestParam(value = "mode", defaultValue = "novel") String mode) {

        Long userId = SecurityUtil.getRequiredUserId();

        // 检查字符配额
        User user = userMapper.selectById(userId);
        if (user != null) {
            // 预估文档字符数（文件大小/2，假设平均每个字符2字节）
            int estimatedChars = (int) Math.ceil(file.getSize() / 2.0);
            // 文档翻译使用专家模式系数
            String quotaMode = "expert";
            if (!quotaService.tryConsumeChars(userId, user.getUserLevel(), estimatedChars, quotaMode)) {
                return Result.error("字符配额不足，请升级档位或等待下月重置", "402");
            }
        }

        try {
            // 创建翻译请求
            DocumentTranslationRequest request = new DocumentTranslationRequest();
            request.setSourceLang(sourceLang);
            request.setTargetLang(targetLang);
            request.setMode(mode);

            // 上传文档
            Document doc = documentService.uploadDocument(userId, file, request);

            // 创建翻译任务
            TranslationTask task = translationTaskService.createDocumentTask(userId, doc);

            // 返回响应
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
            // 路径遍历防护：确保文件路径在 UPLOAD_DIR 白名单范围内
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
