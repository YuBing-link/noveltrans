package com.yumu.noveltranslator.domain.service;

import com.yumu.noveltranslator.dto.entity.DocumentInfoResponse;
import com.yumu.noveltranslator.dto.translation.DocumentTranslationRequest;
import com.yumu.noveltranslator.domain.model.Document;
import com.yumu.noveltranslator.domain.model.TranslationTask;
import com.yumu.noveltranslator.enums.TranslationStatus;
import com.yumu.noveltranslator.port.out.DocumentRepositoryPort;
import com.yumu.noveltranslator.port.out.TranslationRepositoryPort;
import com.yumu.noveltranslator.domain.service.TranslationStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文档管理服务
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    @Value("${translation.upload-dir:#{systemProperties['user.home']}/novel-translator/uploads}")
    private String uploadDir;

    private final DocumentRepositoryPort documentPort;
    private final TranslationRepositoryPort translationPort;
    private final TranslationStateMachine stateMachine;

    /**
     * 上传文档
     */
    public Document uploadDocument(Long userId, MultipartFile file, DocumentTranslationRequest request) throws IOException {
        // 创建上传目录
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";
        String filename = UUID.randomUUID().toString() + extension;
        Path filePath = uploadPath.resolve(filename);

        // 保存文件
        Files.copy(file.getInputStream(), filePath);

        // 创建文档记录
        Document doc = new Document();
        doc.setUserId(userId);
        doc.setName(originalFilename);
        doc.setPath(filePath.toString());
        doc.setSourceLang(request.getSourceLang());
        doc.setTargetLang(request.getTargetLang());
        doc.setFileType(extension.replace(".", "").toLowerCase());
        doc.setFileSize(file.getSize());
        doc.setStatus(TranslationStatus.PENDING.getValue());
        doc.setMode(request.getMode());
        doc.setCreateTime(LocalDateTime.now());

        documentPort.save(doc);

        return doc;
    }

    /**
     * 获取用户文档列表
     */
    public List<Document> getUserDocuments(Long userId, String status) {
        List<Document> documents = documentPort.findByUserId(userId);
        if (status != null && !"all".equals(status)) {
            return documents.stream()
                    .filter(doc -> status.equals(doc.getStatus()))
                    .collect(Collectors.toList());
        }
        return documents;
    }

    /**
     * 获取文档详情
     */
    public Document getDocumentById(Long docId, Long userId) {
        return documentPort.findByIdAndUserId(docId, userId).orElse(null);
    }

    /**
     * 删除文档
     */
    public boolean deleteDocument(Long docId, Long userId) {
        return documentPort.findByIdAndUserId(docId, userId).map(doc -> {
            try {
                Files.deleteIfExists(Paths.get(doc.getPath()));
            } catch (IOException e) {
                // 忽略文件删除失败
            }
            documentPort.markDeleted(docId);
            return true;
        }).orElse(false);
    }

    /**
     * 重新翻译文档（仅重置状态，实际翻译由前端 SSE 触发）
     */
    public boolean retryTranslation(Long docId, Long userId) {
        return documentPort.findByIdAndUserId(docId, userId).map(doc -> {
            doc.setStatus(TranslationStatus.PENDING.getValue());
            doc.setErrorMessage(null);
            doc.setUpdateTime(LocalDateTime.now());
            documentPort.update(doc);
            // 同时重置关联的所有翻译任务状态
            List<TranslationTask> tasks = translationPort.findTasksByDocumentId(docId);
            for (TranslationTask task : tasks) {
                task.setStatus("pending");
                task.setErrorMessage(null);
                task.setProgress(0);
                task.setUpdateTime(LocalDateTime.now());
                translationPort.updateTask(task);
            }
            return true;
        }).orElse(false);
    }

    /**
     * 转换 Document 为 DocumentInfoResponse
     */
    public DocumentInfoResponse toDocumentInfoResponse(Document doc) {
        if (doc == null) {
            return null;
        }
        DocumentInfoResponse response = new DocumentInfoResponse();
        response.setId(doc.getId());
        response.setName(doc.getName());
        response.setFileType(doc.getFileType());
        response.setFileSize(doc.getFileSize());
        response.setSourceLang(doc.getSourceLang());
        response.setTargetLang(doc.getTargetLang());
        response.setTaskId(doc.getTaskId());
        response.setStatus(doc.getStatus());
        response.setProgress(resolveProgress(doc));
        response.setCreateTime(doc.getCreateTime() != null
                ? doc.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null);
        response.setCompletedTime(doc.getCompletedTime() != null
                ? doc.getCompletedTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null);
        response.setErrorMessage(doc.getErrorMessage());
        return response;
    }

    /**
     * 从 Document 或关联的 TranslationTask 获取真实进度
     */
    private int resolveProgress(Document doc) {
        if (TranslationStatus.COMPLETED.getValue().equals(doc.getStatus())) {
            return 100;
        }
        if (TranslationStatus.PROCESSING.getValue().equals(doc.getStatus()) || TranslationStatus.PENDING.getValue().equals(doc.getStatus())) {
            int realProgress = getRealProgress(doc);
            if (realProgress > 0) {
                return realProgress;
            }
            // 返回基于时间的平滑进度，提升用户体验
            if (doc.getCreateTime() != null) {
                long elapsedSeconds = java.time.Duration.between(doc.getCreateTime(), LocalDateTime.now()).getSeconds();
                // 预估 3 分钟完成，每分钟约 33%，最多到 95%
                int estimated = Math.min(95, (int) (elapsedSeconds * 100.0 / 180.0));
                return Math.max(5, estimated); // 至少 5%
            }
        }
        return 0;
    }

    private int getRealProgress(Document doc) {
        if (doc.getTaskId() != null) {
            return translationPort.findTaskByTaskId(doc.getTaskId())
                    .map(task -> task.getProgress() != null ? task.getProgress() : 0)
                    .orElse(0);
        }
        return 0;
    }
}
