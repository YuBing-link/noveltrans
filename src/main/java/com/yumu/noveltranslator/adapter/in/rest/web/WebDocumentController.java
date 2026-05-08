package com.yumu.noveltranslator.adapter.in.rest.web;

import com.yumu.noveltranslator.port.dto.common.Result;
import com.yumu.noveltranslator.port.dto.common.PageResponse;
import com.yumu.noveltranslator.port.dto.entity.DocumentInfoResponse;
import com.yumu.noveltranslator.port.dto.translation.DocumentTranslationRequest;
import com.yumu.noveltranslator.port.dto.translation.DocumentTranslationResponse;
import com.yumu.noveltranslator.domain.model.Document;
import com.yumu.noveltranslator.enums.ErrorCodeEnum;
import com.yumu.noveltranslator.enums.TranslationStatus;
import com.yumu.noveltranslator.port.in.CollabPort;
import com.yumu.noveltranslator.port.in.DocumentPort;
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

/**
 * Web 文档管理接口
 * 路径前缀: /user/documents
 */
@RestController
@RequestMapping("/user/documents")
@RequiredArgsConstructor
public class WebDocumentController {

    private final DocumentPort documentPort;
    private final CollabPort collabPort;

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

        List<Document> documents = documentPort.getUserDocuments(userId, status);
        List<DocumentInfoResponse> responseList = documents.stream()
                .map(documentPort::toDocumentInfoResponse)
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

        Document doc = documentPort.getDocumentById(docId, userId);
        if (doc == null) {
            return Result.error(ErrorCodeEnum.NOT_FOUND, "文档不存在");
        }

        return Result.ok(documentPort.toDocumentInfoResponse(doc));
    }

    /**
     * 删除文档
     * DELETE /user/documents/{docId}
     */
    @DeleteMapping("/{docId}")
    public Result<Void> deleteDocument(@PathVariable Long docId) {
        Long userId = SecurityUtil.getRequiredUserId();

        if (documentPort.deleteDocument(docId, userId)) {
            return Result.ok(null);
        } else {
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR, "删除失败");
        }
    }

    /**
     * 取消翻译
     * POST /user/documents/{docId}/cancel
     */
    @PostMapping("/{docId}/cancel")
    public Result<Void> cancelTranslation(@PathVariable Long docId) {
        Long userId = SecurityUtil.getRequiredUserId();

        if (documentPort.cancelTranslation(docId, userId)) {
            return Result.ok(null);
        } else {
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR, "取消失败");
        }
    }

    /**
     * 重新翻译
     * POST /user/documents/{docId}/retry
     */
    @PostMapping("/{docId}/retry")
    public Result<Void> retryDocument(@PathVariable Long docId) {
        Long userId = SecurityUtil.getRequiredUserId();

        if (documentPort.retryTranslation(docId, userId)) {
            return Result.ok(null);
        } else {
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR, "重试失败，文档不存在");
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
            @RequestParam(value = "mode", defaultValue = "fast") String mode,
            @RequestParam(value = "projectId", required = false) Long projectId) {

        Long userId = SecurityUtil.getRequiredUserId();

        try {
            DocumentTranslationRequest request = new DocumentTranslationRequest();
            request.setSourceLang(sourceLang);
            request.setTargetLang(targetLang);
            request.setMode(mode);

            // 团队模式：指定已有项目 or 自动创建新项目
            if ("team".equals(mode)) {
                Document doc = documentPort.uploadDocument(userId, file, request);

                if (projectId != null) {
                    int chapterCount = collabPort.addChaptersToProject(userId, projectId, doc);
                    collabPort.startMultiAgentTranslation(projectId);

                    DocumentTranslationResponse response = new DocumentTranslationResponse();
                    response.setTaskId(null);
                    response.setDocumentId(doc.getId());
                    response.setDocumentName(doc.getName());
                    response.setStatus(TranslationStatus.PENDING.getValue());
                    response.setProjectId(projectId);
                    response.setMessage("已添加 " + chapterCount + " 个章节到协作项目");
                    return Result.ok(response);
                } else {
                    CollabPort.TeamProjectCreateResult result =
                            collabPort.createProjectFromDocument(
                                    userId, doc.getId(), doc.getName(), doc.getPath(), doc.getFileType(),
                                    sourceLang, targetLang);

                    collabPort.startMultiAgentTranslation(result.projectId());

                    DocumentTranslationResponse response = new DocumentTranslationResponse();
                    response.setTaskId(null);
                    response.setDocumentId(doc.getId());
                    response.setDocumentName(doc.getName());
                    response.setStatus(TranslationStatus.PENDING.getValue());
                    response.setProjectId(result.projectId());
                    response.setMessage("团队模式已创建项目，共 " + result.chapterCount() + " 个章节");
                    return Result.ok(response);
                }
            }

            // fast/expert 模式：上传文档并自动启动翻译
            DocumentTranslationResponse response = documentPort.uploadAndStartTranslation(userId, file, request);
            return Result.ok(response);

        } catch (IOException e) {
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR, "文件上传失败：" + e.getMessage());
        }
    }

    /**
     * 下载翻译后的文档
     * GET /user/documents/{docId}/download
     */
    @GetMapping("/{docId}/download")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long docId) {
        Long userId = SecurityUtil.getRequiredUserId();

        Document doc = documentPort.getDocumentById(docId, userId);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            // 优先返回翻译后的文件
            String downloadPath;
            if (TranslationStatus.COMPLETED.getValue().equals(doc.getStatus())) {
                int lastDot = doc.getPath().lastIndexOf('.');
                String translatedPathStr = lastDot > 0
                        ? doc.getPath().substring(0, lastDot) + "_translated" + doc.getPath().substring(lastDot)
                        : doc.getPath() + "_translated";
                Path translatedPath = Paths.get(translatedPathStr).normalize().toAbsolutePath();
                if (Files.exists(translatedPath)) {
                    downloadPath = translatedPathStr;
                } else {
                    downloadPath = doc.getPath();
                }
            } else {
                downloadPath = doc.getPath();
            }

            Path targetPath = Paths.get(downloadPath).normalize().toAbsolutePath();
            if (!Files.exists(targetPath)) {
                return ResponseEntity.notFound().build();
            }

            byte[] fileContent = Files.readAllBytes(targetPath);

            // Determine filename and content type from file extension
            String downloadFileName = doc.getName();
            if (TranslationStatus.COMPLETED.getValue().equals(doc.getStatus())) {
                int dot = downloadFileName.lastIndexOf('.');
                downloadFileName = (dot > 0 ? downloadFileName.substring(0, dot) : downloadFileName) + "_translated" + downloadFileName.substring(dot);
            }

            String ext = downloadFileName.substring(downloadFileName.lastIndexOf('.') + 1).toLowerCase();
            MediaType mediaType = switch (ext) {
                case "txt" -> MediaType.TEXT_PLAIN;
                case "html", "htm" -> MediaType.TEXT_HTML;
                case "xml" -> MediaType.APPLICATION_XML;
                case "json" -> MediaType.APPLICATION_JSON;
                case "pdf" -> MediaType.parseMediaType("application/pdf");
                case "epub" -> MediaType.parseMediaType("application/epub+zip");
                case "docx" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                default -> MediaType.APPLICATION_OCTET_STREAM;
            };

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(mediaType);
            // Use both ASCII fallback and RFC 5987 for UTF-8 filenames
            String asciiName = downloadFileName.replaceAll("[^\\x20-\\x7E]", "_");
            String encodedName = java.net.URLEncoder.encode(downloadFileName, java.nio.charset.StandardCharsets.UTF_8);
            String contentDisposition = String.format(
                    "attachment; filename=\"%s\"; filename*=UTF-8''%s",
                    asciiName, encodedName);
            headers.add("Content-Disposition", contentDisposition);

            return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
