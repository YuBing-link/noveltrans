package com.yumu.noveltranslator.port.in;

import com.yumu.noveltranslator.domain.model.Document;
import com.yumu.noveltranslator.port.dto.entity.DocumentInfoResponse;
import com.yumu.noveltranslator.port.dto.translation.DocumentTranslationRequest;
import com.yumu.noveltranslator.port.dto.translation.DocumentTranslationResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface DocumentPort {
    Document uploadDocument(Long userId, MultipartFile file, DocumentTranslationRequest request) throws IOException;
    List<Document> getUserDocuments(Long userId, String status);
    Document getDocumentById(Long docId, Long userId);
    boolean deleteDocument(Long docId, Long userId);
    boolean retryTranslation(Long docId, Long userId);
    void updateDocument(Document doc, Long userId);
    DocumentInfoResponse toDocumentInfoResponse(Document doc);

    /**
     * 上传文档并根据翻译模式自动启动翻译
     * 团队模式：调用协作项目接口；fast/expert 模式：创建翻译任务
     */
    DocumentTranslationResponse uploadAndStartTranslation(
            Long userId, MultipartFile file, DocumentTranslationRequest request) throws IOException;

    /**
     * 取消翻译：先尝试取消翻译任务，无任务时直接更新文档状态
     */
    boolean cancelTranslation(Long docId, Long userId);
}
