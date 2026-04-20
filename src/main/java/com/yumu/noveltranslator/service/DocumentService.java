package com.yumu.noveltranslator.service;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.Document;
import com.yumu.noveltranslator.entity.TranslationHistory;
import com.yumu.noveltranslator.entity.TranslationTask;
import com.yumu.noveltranslator.enums.TranslationStatus;
import com.yumu.noveltranslator.mapper.DocumentMapper;
import com.yumu.noveltranslator.mapper.TranslationHistoryMapper;
import com.yumu.noveltranslator.mapper.TranslationTaskMapper;
import com.yumu.noveltranslator.service.state.TranslationStateMachine;
import org.springframework.beans.factory.annotation.Autowired;
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
public class DocumentService {

    @Value("${translation.upload-dir:#{systemProperties['user.home']}/novel-translator/uploads}")
    private String uploadDir;

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private TranslationTaskMapper translationTaskMapper;

    @Autowired
    private TranslationStateMachine stateMachine;

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

        documentMapper.insert(doc);

        return doc;
    }

    /**
     * 获取用户文档列表
     */
    public List<Document> getUserDocuments(Long userId, String status) {
        List<Document> documents = documentMapper.findByUserId(userId);
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
        return documentMapper.findByIdAndUserId(docId, userId);
    }

    /**
     * 删除文档
     */
    public boolean deleteDocument(Long docId, Long userId) {
        Document doc = documentMapper.findByIdAndUserId(docId, userId);
        if (doc != null) {
            // 删除文件
            try {
                Files.deleteIfExists(Paths.get(doc.getPath()));
            } catch (IOException e) {
                // 忽略文件删除失败
            }
            // 逻辑删除 - 使用直接更新避免 MyBatis-Plus @TableLogic 干扰
            documentMapper.updateDeletedStatus(docId);
            return true;
        }
        return false;
    }

    /**
     * 重新翻译文档（仅重置状态，实际翻译由前端 SSE 触发）
     */
    public boolean retryTranslation(Long docId, Long userId) {
        Document doc = documentMapper.findByIdAndUserId(docId, userId);
        if (doc != null) {
            doc.setStatus(TranslationStatus.PENDING.getValue());
            doc.setErrorMessage(null);
            doc.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(doc);
            // 同时重置关联的翻译任务状态
            TranslationTask task = translationTaskMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TranslationTask>()
                            .eq(TranslationTask::getDocumentId, docId));
            if (task != null) {
                task.setStatus("pending");
                task.setErrorMessage(null);
                task.setProgress(0);
                task.setUpdateTime(LocalDateTime.now());
                translationTaskMapper.updateById(task);
            }
            return true;
        }
        return false;
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
        response.setProgress(100); // 简化处理
        response.setCreateTime(doc.getCreateTime() != null
                ? doc.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null);
        response.setCompletedTime(doc.getCompletedTime() != null
                ? doc.getCompletedTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null);
        response.setErrorMessage(doc.getErrorMessage());
        return response;
    }
}
