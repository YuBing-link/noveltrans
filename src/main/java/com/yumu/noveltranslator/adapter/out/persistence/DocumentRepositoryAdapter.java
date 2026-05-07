package com.yumu.noveltranslator.adapter.out.persistence;

import com.yumu.noveltranslator.adapter.out.persistence.entity.Document;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.DocumentMapper;
import com.yumu.noveltranslator.port.out.DocumentRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DocumentRepositoryAdapter implements DocumentRepositoryPort {

    private final DocumentMapper documentMapper;

    @Override
    public Optional<Document> findById(Long id) {
        return Optional.ofNullable(documentMapper.findById(id));
    }

    @Override
    public Optional<Document> findByIdAndUserId(Long id, Long userId) {
        return Optional.ofNullable(documentMapper.findByIdAndUserId(id, userId));
    }

    @Override
    public List<Document> findByUserId(Long userId) {
        return documentMapper.findByUserId(userId);
    }

    @Override
    public void save(Document document) {
        documentMapper.insert(document);
    }

    @Override
    public void update(Document document) {
        documentMapper.updateById(document);
    }

    @Override
    public void markDeleted(Long id) {
        documentMapper.updateDeletedStatus(id);
    }
}
