package com.yumu.noveltranslator.event;

import java.util.List;

/**
 * 章节拆分事件，在创建项目时发布，由异步监听器处理批量章节插入。
 */
public class ChapterSplitEvent {

    private final Long projectId;
    private final Long userId;
    private final Long documentId;
    private final String documentName;
    private final List<String> chapters;
    private final String sourceLang;
    private final String targetLang;
    private final long timestamp;

    public ChapterSplitEvent(Long projectId, Long userId, Long documentId, String documentName,
                              List<String> chapters, String sourceLang, String targetLang) {
        this.projectId = projectId;
        this.userId = userId;
        this.documentId = documentId;
        this.documentName = documentName;
        this.chapters = List.copyOf(chapters);
        this.sourceLang = sourceLang;
        this.targetLang = targetLang;
        this.timestamp = System.currentTimeMillis();
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public String getDocumentName() {
        return documentName;
    }

    public List<String> getChapters() {
        return chapters;
    }

    public String getSourceLang() {
        return sourceLang;
    }

    public String getTargetLang() {
        return targetLang;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
