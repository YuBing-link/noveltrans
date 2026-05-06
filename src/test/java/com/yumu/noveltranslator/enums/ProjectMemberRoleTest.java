package com.yumu.noveltranslator.enums;
import com.yumu.noveltranslator.exception.BusinessException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProjectMemberRole 单元测试")
class ProjectMemberRoleTest {

    @Nested
    @DisplayName("枚举值测试")
    class EnumValueTests {

        @Test
        @DisplayName("OWNER的value为OWNER")
        void ownerValue为OWNER() {
            assertEquals("OWNER", ProjectMemberRole.OWNER.getValue());
        }

        @Test
        @DisplayName("REVIEWER的value为REVIEWER")
        void reviewerValue为REVIEWER() {
            assertEquals("REVIEWER", ProjectMemberRole.REVIEWER.getValue());
        }

        @Test
        @DisplayName("TRANSLATOR的value为TRANSLATOR")
        void translatorValue为TRANSLATOR() {
            assertEquals("TRANSLATOR", ProjectMemberRole.TRANSLATOR.getValue());
        }
    }

    @Nested
    @DisplayName("satisfies方法测试")
    class SatisfiesTests {

        @Test
        @DisplayName("OWNER满足所有角色要求")
        void owner满足所有角色() {
            assertTrue(ProjectMemberRole.OWNER.satisfies(ProjectMemberRole.OWNER));
            assertTrue(ProjectMemberRole.OWNER.satisfies(ProjectMemberRole.REVIEWER));
            assertTrue(ProjectMemberRole.OWNER.satisfies(ProjectMemberRole.TRANSLATOR));
        }

        @Test
        @DisplayName("REVIEWER满足REVIEWER和TRANSLATOR但不满足OWNER")
        void reviewer满足部分角色() {
            assertFalse(ProjectMemberRole.REVIEWER.satisfies(ProjectMemberRole.OWNER));
            assertTrue(ProjectMemberRole.REVIEWER.satisfies(ProjectMemberRole.REVIEWER));
            assertTrue(ProjectMemberRole.REVIEWER.satisfies(ProjectMemberRole.TRANSLATOR));
        }

        @Test
        @DisplayName("TRANSLATOR只满足TRANSLATOR")
        void translator只满足自己() {
            assertFalse(ProjectMemberRole.TRANSLATOR.satisfies(ProjectMemberRole.OWNER));
            assertFalse(ProjectMemberRole.TRANSLATOR.satisfies(ProjectMemberRole.REVIEWER));
            assertTrue(ProjectMemberRole.TRANSLATOR.satisfies(ProjectMemberRole.TRANSLATOR));
        }
    }

    @Nested
    @DisplayName("fromValue方法测试")
    class FromValueTests {

        @Test
        @DisplayName("fromValue有效值返回正确枚举")
        void 有效值返回正确枚举() {
            assertEquals(ProjectMemberRole.OWNER, ProjectMemberRole.fromValue("OWNER"));
            assertEquals(ProjectMemberRole.REVIEWER, ProjectMemberRole.fromValue("REVIEWER"));
            assertEquals(ProjectMemberRole.TRANSLATOR, ProjectMemberRole.fromValue("TRANSLATOR"));
        }

        @Test
        @DisplayName("fromValue无效值抛出IllegalArgumentException")
        void 无效值抛出异常() {
            assertThrows(IllegalArgumentException.class, () -> ProjectMemberRole.fromValue("INVALID"));
            assertThrows(IllegalArgumentException.class, () -> ProjectMemberRole.fromValue(""));
            assertThrows(IllegalArgumentException.class, () -> ProjectMemberRole.fromValue(null));
        }
    }
}
