package com.yumu.noveltranslator.adapter.out.persistence;

import com.yumu.noveltranslator.adapter.out.persistence.converter.TranslationConverter;
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
    public Optional<com.yumu.noveltranslator.domain.model.Document> findById(Long id) {
        return Optional.ofNullable(TranslationConverter.toModelDocument(documentMapper.findById(id)));
    }

    @Override
    public Optional<com.yumu.noveltranslator.domain.model.Document> findByIdAndUserId(Long id, Long userId) {
        return Optional.ofNullable(TranslationConverter.toModelDocument(documentMapper.findByIdAndUserId(id, userId)));
    }

    @Override
    public List<com.yumu.noveltranslator.domain.model.Document> findByUserId(Long userId) {
        return TranslationConverter.toModelDocuments(documentMapper.findByUserId(userId));
    }

    @Override
    public void save(com.yumu.noveltranslator.domain.model.Document document) {
        var entity = TranslationConverter.toEntityDocument(document);
        documentMapper.insert(entity);
        document.setId(entity.getId()); // 回填自增主键
    }

    @Override
    public void update(com.yumu.noveltranslator.domain.model.Document document) {
        documentMapper.updateById(TranslationConverter.toEntityDocument(document));
    }

    @Override
    public void markDeleted(Long id) {
        documentMapper.updateDeletedStatus(id);
    }
}
