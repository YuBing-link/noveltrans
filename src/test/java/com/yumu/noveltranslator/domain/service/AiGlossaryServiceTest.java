package com.yumu.noveltranslator.domain.service;
import com.yumu.noveltranslator.domain.service.AiGlossaryService;

import com.yumu.noveltranslator.adapter.out.persistence.entity.AiGlossary;
import com.yumu.noveltranslator.port.out.GlossaryRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiGlossaryServiceTest {

    @Mock
    private GlossaryRepositoryPort glossaryPort;

    private AiGlossaryService service;

    @BeforeEach
    void setUp() {
        service = new AiGlossaryService(glossaryPort);
    }

    @Nested
    @DisplayName("获取项目术语")
    class GetProjectGlossaryTests {

        @Test
        void 返回已确认术语() {
            AiGlossary term = buildTerm("hello", "你好", "confirmed");
            when(glossaryPort.findAiGlossaryByProjectIdAndStatus(1L, "confirmed"))
                .thenReturn(List.of(term));

            List<AiGlossary> result = service.getProjectGlossary(1L);

            assertEquals(1, result.size());
            assertEquals("hello", result.get(0).getSourceWord());
        }

        @Test
        void 无术语返回空列表() {
            when(glossaryPort.findAiGlossaryByProjectIdAndStatus(1L, "confirmed"))
                .thenReturn(List.of());

            List<AiGlossary> result = service.getProjectGlossary(1L);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("添加术语(upsert)")
    class AddTermTests {

        @Test
        void 新术语upsert() {
            service.addTerm(1L, "hello", "你好", "context", "character", 5L);

            verify(glossaryPort).upsertAiGlossary(eq(1L), eq("hello"), eq("你好"),
                    eq("context"), eq("character"), eq(5L));
        }
    }

    @Nested
    @DisplayName("批量添加术语")
    class BatchAddTermsTests {

        @Test
        void 批量插入() {
            List<AiGlossary> terms = List.of(
                buildTerm("hello", "你好", null),
                buildTerm("world", "世界", null)
            );

            service.batchAddTerms(1L, terms);

            verify(glossaryPort, times(2)).upsertAiGlossary(anyLong(), anyString(), anyString(),
                    anyString(), anyString(), anyLong());
        }

        @Test
        void 空列表不处理() {
            service.batchAddTerms(1L, null);
            service.batchAddTerms(1L, List.of());

            verifyNoInteractions(glossaryPort);
        }

        @Test
        void 设置默认状态和置信度() {
            AiGlossary term = new AiGlossary();
            term.setSourceWord("test");
            term.setTargetWord("测试");
            // status and confidence not set

            service.batchAddTerms(1L, List.of(term));

            verify(glossaryPort).upsertAiGlossary(eq(1L), eq("test"), eq("测试"),
                    isNull(), isNull(), isNull());
        }
    }

    @Nested
    @DisplayName("更新术语状态")
    class UpdateTermStatusTests {

        @Test
        void 成功更新() {
            AiGlossary term = buildTerm("hello", "你好", "pending");
            term.setId(10L);
            when(glossaryPort.findAiGlossaryById(10L)).thenReturn(Optional.of(term));

            boolean result = service.updateTermStatus(10L, "confirmed");

            assertTrue(result);
            assertEquals("confirmed", term.getStatus());
            verify(glossaryPort).updateAiGlossary(term);
        }

        @Test
        void 术语不存在返回false() {
            when(glossaryPort.findAiGlossaryById(999L)).thenReturn(Optional.empty());

            boolean result = service.updateTermStatus(999L, "confirmed");

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("删除术语")
    class DeleteTermTests {

        @Test
        void 删除成功() {
            doNothing().when(glossaryPort).deleteAiGlossary(10L);

            boolean result = service.deleteTerm(10L);

            assertTrue(result);
        }

        @Test
        void 术语不存在也返回true() {
            // service always calls deleteAiGlossary and returns true
            doNothing().when(glossaryPort).deleteAiGlossary(999L);

            boolean result = service.deleteTerm(999L);

            assertTrue(result);
        }
    }

    @Nested
    @DisplayName("获取待确认术语")
    class GetPendingTermsTests {

        @Test
        void 返回pending术语() {
            AiGlossary term = buildTerm("hello", "你好", "pending");
            when(glossaryPort.findAiGlossaryByProjectIdAndStatus(1L, "pending"))
                .thenReturn(List.of(term));

            List<AiGlossary> result = service.getPendingTerms(1L);

            assertEquals(1, result.size());
            assertEquals("pending", result.get(0).getStatus());
        }
    }

    private AiGlossary buildTerm(String source, String target, String status) {
        AiGlossary term = new AiGlossary();
        term.setId(1L);
        term.setProjectId(1L);
        term.setSourceWord(source);
        term.setTargetWord(target);
        term.setContext("test context");
        term.setEntityType("character");
        term.setChapterId(1L);
        term.setStatus(status);
        term.setConfidence(0.8);
        return term;
    }
}
