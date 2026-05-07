package com.yumu.noveltranslator.port.out;

import com.yumu.noveltranslator.adapter.out.persistence.entity.Document;

import java.util.List;
import java.util.Optional;

public interface DocumentRepositoryPort {
    Optional<Document> findById(Long id);
    Optional<Document> findByIdAndUserId(Long id, Long userId);
    List<Document> findByUserId(Long userId);
    void save(Document document);
    void update(Document document);
    void markDeleted(Long id);
}
