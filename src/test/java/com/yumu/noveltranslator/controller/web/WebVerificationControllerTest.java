package com.yumu.noveltranslator.controller.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebVerificationControllerTest {

    private final WebVerificationController controller = new WebVerificationController();

    @Nested
    @DisplayName("验证页面路由")
    class VerificationPageTests {

        @Test
        void verification路径返回verification视图() {
            assertEquals("verification", controller.verificationPage());
        }

        @Test
        void register路径返回verification视图() {
            assertEquals("verification", controller.registerPage());
        }
    }
}
