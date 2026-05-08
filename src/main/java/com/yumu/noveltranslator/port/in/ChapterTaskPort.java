package com.yumu.noveltranslator.port.in;

import com.yumu.noveltranslator.port.dto.collab.ChapterTaskResponse;
import com.yumu.noveltranslator.port.dto.common.PageResponse;

public interface ChapterTaskPort {
    ChapterTaskResponse createChapter(Long projectId, Integer chapterNumber, String title, String sourceText, Long creatorId);
    PageResponse<ChapterTaskResponse> listByProjectId(Long projectId, int page, int pageSize);
    ChapterTaskResponse getChapterById(Long chapterId, Long userId);
    ChapterTaskResponse assignChapter(Long chapterId, Long assigneeId, Long assignerId);
    ChapterTaskResponse submitChapter(Long chapterId, String translatedText);
    ChapterTaskResponse reviewChapter(Long chapterId, Boolean approved, String comment, Long reviewerId);
    PageResponse<ChapterTaskResponse> listByAssigneeId(Long assigneeId, int page, int pageSize);
}
