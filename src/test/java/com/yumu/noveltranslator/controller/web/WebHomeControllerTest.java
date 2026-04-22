package com.yumu.noveltranslator.controller.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebHomeControllerTest {

    private final WebHomeController controller = new WebHomeController();

    @Nested
    @DisplayName("首页路由")
    class HomeTests {

        @Test
        void 根路径返回index视图() {
            assertEquals("index", controller.home());
        }

        @Test
        void home路径返回index视图() {
            assertEquals("index", controller.homePage());
        }
    }
}
