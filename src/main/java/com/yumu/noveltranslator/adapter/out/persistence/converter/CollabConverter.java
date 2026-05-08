package com.yumu.noveltranslator.adapter.out.persistence.converter;

import com.yumu.noveltranslator.adapter.out.persistence.entity.*;

import java.util.List;
import java.util.stream.Collectors;

public final class CollabConverter {

    private CollabConverter() {}

    // === CollabProject ===

    public static com.yumu.noveltranslator.domain.model.CollabProject toProjectModel(CollabProject entity) {
        if (entity == null) return null;
        var model = new com.yumu.noveltranslator.domain.model.CollabProject();
        model.setId(entity.getId());
        model.setName(entity.getName());
        model.setDescription(entity.getDescription());
        model.setOwnerId(entity.getOwnerId());
        model.setDocumentId(entity.getDocumentId());
        model.setSourceLang(entity.getSourceLang());
        model.setTargetLang(entity.getTargetLang());
        model.setStatus(entity.getStatus());
        model.setProgress(entity.getProgress());
        model.setTenantId(entity.getTenantId());
        model.setCreateTime(entity.getCreateTime());
        model.setUpdateTime(entity.getUpdateTime());
        model.setDeleted(entity.getDeleted());
        return model;
    }

    public static CollabProject toProjectEntity(com.yumu.noveltranslator.domain.model.CollabProject model) {
        if (model == null) return null;
        var entity = new CollabProject();
        entity.setId(model.getId());
        entity.setName(model.getName());
        entity.setDescription(model.getDescription());
        entity.setOwnerId(model.getOwnerId());
        entity.setDocumentId(model.getDocumentId());
        entity.setSourceLang(model.getSourceLang());
        entity.setTargetLang(model.getTargetLang());
        entity.setStatus(model.getStatus());
        entity.setProgress(model.getProgress());
        entity.setTenantId(model.getTenantId());
        entity.setCreateTime(model.getCreateTime());
        entity.setUpdateTime(model.getUpdateTime());
        entity.setDeleted(model.getDeleted());
        return entity;
    }

    public static List<com.yumu.noveltranslator.domain.model.CollabProject> toProjectModelList(List<CollabProject> entities) {
        if (entities == null) return List.of();
        return entities.stream().map(CollabConverter::toProjectModel).collect(Collectors.toList());
    }

    // === CollabProjectMember ===

    public static com.yumu.noveltranslator.domain.model.CollabProjectMember toMemberModel(CollabProjectMember entity) {
        if (entity == null) return null;
        var model = new com.yumu.noveltranslator.domain.model.CollabProjectMember();
        model.setId(entity.getId());
        model.setProjectId(entity.getProjectId());
        model.setUserId(entity.getUserId());
        model.setRole(entity.getRole());
        model.setInviteCode(entity.getInviteCode());
        model.setInviteStatus(entity.getInviteStatus());
        model.setJoinedTime(entity.getJoinedTime());
        model.setTenantId(entity.getTenantId());
        model.setCreateTime(entity.getCreateTime());
        model.setDeleted(entity.getDeleted());
        return model;
    }

    public static CollabProjectMember toMemberEntity(com.yumu.noveltranslator.domain.model.CollabProjectMember model) {
        if (model == null) return null;
        var entity = new CollabProjectMember();
        entity.setId(model.getId());
        entity.setProjectId(model.getProjectId());
        entity.setUserId(model.getUserId());
        entity.setRole(model.getRole());
        entity.setInviteCode(model.getInviteCode());
        entity.setInviteStatus(model.getInviteStatus());
        entity.setJoinedTime(model.getJoinedTime());
        entity.setTenantId(model.getTenantId());
        entity.setCreateTime(model.getCreateTime());
        entity.setDeleted(model.getDeleted());
        return entity;
    }

    public static List<com.yumu.noveltranslator.domain.model.CollabProjectMember> toMemberModelList(List<CollabProjectMember> entities) {
        if (entities == null) return List.of();
        return entities.stream().map(CollabConverter::toMemberModel).collect(Collectors.toList());
    }

    // === CollabChapterTask ===

    public static com.yumu.noveltranslator.domain.model.CollabChapterTask toChapterTaskModel(CollabChapterTask entity) {
        if (entity == null) return null;
        var model = new com.yumu.noveltranslator.domain.model.CollabChapterTask();
        model.setId(entity.getId());
        model.setProjectId(entity.getProjectId());
        model.setChapterNumber(entity.getChapterNumber());
        model.setTitle(entity.getTitle());
        model.setSourceText(entity.getSourceText());
        model.setTargetText(entity.getTargetText());
        model.setAssigneeId(entity.getAssigneeId());
        model.setReviewerId(entity.getReviewerId());
        model.setStatus(entity.getStatus());
        model.setReviewComment(entity.getReviewComment());
        model.setProgress(entity.getProgress());
        model.setSourceWordCount(entity.getSourceWordCount());
        model.setTargetWordCount(entity.getTargetWordCount());
        model.setTenantId(entity.getTenantId());
        model.setCreateTime(entity.getCreateTime());
        model.setUpdateTime(entity.getUpdateTime());
        model.setAssignedTime(entity.getAssignedTime());
        model.setSubmittedTime(entity.getSubmittedTime());
        model.setReviewedTime(entity.getReviewedTime());
        model.setRetryCount(entity.getRetryCount());
        model.setCompletedTime(entity.getCompletedTime());
        model.setDeleted(entity.getDeleted());
        return model;
    }

    public static CollabChapterTask toChapterTaskEntity(com.yumu.noveltranslator.domain.model.CollabChapterTask model) {
        if (model == null) return null;
        var entity = new CollabChapterTask();
        entity.setId(model.getId());
        entity.setProjectId(model.getProjectId());
        entity.setChapterNumber(model.getChapterNumber());
        entity.setTitle(model.getTitle());
        entity.setSourceText(model.getSourceText());
        entity.setTargetText(model.getTargetText());
        entity.setAssigneeId(model.getAssigneeId());
        entity.setReviewerId(model.getReviewerId());
        entity.setStatus(model.getStatus());
        entity.setReviewComment(model.getReviewComment());
        entity.setProgress(model.getProgress());
        entity.setSourceWordCount(model.getSourceWordCount());
        entity.setTargetWordCount(model.getTargetWordCount());
        entity.setTenantId(model.getTenantId());
        entity.setCreateTime(model.getCreateTime());
        entity.setUpdateTime(model.getUpdateTime());
        entity.setAssignedTime(model.getAssignedTime());
        entity.setSubmittedTime(model.getSubmittedTime());
        entity.setReviewedTime(model.getReviewedTime());
        entity.setRetryCount(model.getRetryCount());
        entity.setCompletedTime(model.getCompletedTime());
        entity.setDeleted(model.getDeleted());
        return entity;
    }

    public static List<com.yumu.noveltranslator.domain.model.CollabChapterTask> toChapterTaskModelList(List<CollabChapterTask> entities) {
        if (entities == null) return List.of();
        return entities.stream().map(CollabConverter::toChapterTaskModel).collect(Collectors.toList());
    }

    // === CollabComment ===

    public static com.yumu.noveltranslator.domain.model.CollabComment toCommentModel(CollabComment entity) {
        if (entity == null) return null;
        var model = new com.yumu.noveltranslator.domain.model.CollabComment();
        model.setId(entity.getId());
        model.setChapterTaskId(entity.getChapterTaskId());
        model.setUserId(entity.getUserId());
        model.setSourceText(entity.getSourceText());
        model.setTargetText(entity.getTargetText());
        model.setContent(entity.getContent());
        model.setParentId(entity.getParentId());
        model.setResolved(entity.getResolved());
        model.setTenantId(entity.getTenantId());
        model.setCreateTime(entity.getCreateTime());
        model.setUpdateTime(entity.getUpdateTime());
        model.setDeleted(entity.getDeleted());
        return model;
    }

    public static CollabComment toCommentEntity(com.yumu.noveltranslator.domain.model.CollabComment model) {
        if (model == null) return null;
        var entity = new CollabComment();
        entity.setId(model.getId());
        entity.setChapterTaskId(model.getChapterTaskId());
        entity.setUserId(model.getUserId());
        entity.setSourceText(model.getSourceText());
        entity.setTargetText(model.getTargetText());
        entity.setContent(model.getContent());
        entity.setParentId(model.getParentId());
        entity.setResolved(model.getResolved());
        entity.setTenantId(model.getTenantId());
        entity.setCreateTime(model.getCreateTime());
        entity.setUpdateTime(model.getUpdateTime());
        entity.setDeleted(model.getDeleted());
        return entity;
    }

    public static List<com.yumu.noveltranslator.domain.model.CollabComment> toCommentModelList(List<CollabComment> entities) {
        if (entities == null) return List.of();
        return entities.stream().map(CollabConverter::toCommentModel).collect(Collectors.toList());
    }

    // === CollabInviteCode ===

    public static com.yumu.noveltranslator.domain.model.CollabInviteCode toInviteCodeModel(CollabInviteCode entity) {
        if (entity == null) return null;
        var model = new com.yumu.noveltranslator.domain.model.CollabInviteCode();
        model.setId(entity.getId());
        model.setProjectId(entity.getProjectId());
        model.setCode(entity.getCode());
        model.setExpiresAt(entity.getExpiresAt());
        model.setUsed(entity.getUsed());
        model.setTenantId(entity.getTenantId());
        model.setCreateTime(entity.getCreateTime());
        model.setDeleted(entity.getDeleted());
        return model;
    }

    public static CollabInviteCode toInviteCodeEntity(com.yumu.noveltranslator.domain.model.CollabInviteCode model) {
        if (model == null) return null;
        var entity = new CollabInviteCode();
        entity.setId(model.getId());
        entity.setProjectId(model.getProjectId());
        entity.setCode(model.getCode());
        entity.setExpiresAt(model.getExpiresAt());
        entity.setUsed(model.getUsed());
        entity.setTenantId(model.getTenantId());
        entity.setCreateTime(model.getCreateTime());
        entity.setDeleted(model.getDeleted());
        return entity;
    }

    public static List<com.yumu.noveltranslator.domain.model.CollabInviteCode> toInviteCodeModelList(List<CollabInviteCode> entities) {
        if (entities == null) return List.of();
        return entities.stream().map(CollabConverter::toInviteCodeModel).collect(Collectors.toList());
    }
}
