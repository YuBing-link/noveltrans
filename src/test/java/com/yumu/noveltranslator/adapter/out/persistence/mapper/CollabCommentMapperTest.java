package com.yumu.noveltranslator.adapter.out.persistence.mapper;
import com.yumu.noveltranslator.adapter.out.persistence.mapper.CollabCommentMapper;

import com.yumu.noveltranslator.config.tenant.TenantContext;
import com.yumu.noveltranslator.adapter.out.persistence.entity.CollabComment;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CollabCommentMapper 自定义 SQL 集成测试
 */
@Disabled("Requires real MySQL — Spring context fails in CI due to sqlSessionTemplate initialization")
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CollabCommentMapperTest {

    @Autowired
    private CollabCommentMapper collabCommentMapper;

    @Test
    @Order(1)
    @DisplayName("selectByChapterTaskId - 按章节任务查询顶级评论")
    void selectByChapterTaskId() {
        try {
            TenantContext.setBypassTenant(true);
            List<CollabComment> comments = collabCommentMapper.selectByChapterTaskId(999999L);
            assertNotNull(comments);
            assertTrue(comments.isEmpty());
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }

    @Test
    @Order(2)
    @DisplayName("selectRepliesByParentId - 按父评论 ID 查询回复")
    void selectRepliesByParentId() {
        try {
            TenantContext.setBypassTenant(true);
            List<CollabComment> replies = collabCommentMapper.selectRepliesByParentId(999999L);
            assertNotNull(replies);
            assertTrue(replies.isEmpty());
        } finally {
            TenantContext.setBypassTenant(false);
        }
    }
}
