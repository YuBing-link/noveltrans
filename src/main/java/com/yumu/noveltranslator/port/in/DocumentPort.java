package com.yumu.noveltranslator.port.in;

import com.yumu.noveltranslator.domain.model.Document;
import com.yumu.noveltranslator.port.dto.entity.DocumentInfoResponse;
import com.yumu.noveltranslator.port.dto.translation.DocumentTranslationRequest;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface DocumentPort {
    Document uploadDocument(Long userId, MultipartFile file, DocumentTranslationRequest request) throws IOException;
    List<Document> getUserDocuments(Long userId, String status);
    Document getDocumentById(Long docId, Long userId);
    boolean deleteDocument(Long docId, Long userId);
    boolean retryTranslation(Long docId, Long userId);
    DocumentInfoResponse toDocumentInfoResponse(Document doc);
}
