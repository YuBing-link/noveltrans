package com.yumu.noveltranslator;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 集成测试，需要完整的数据库环境，在 Docker Compose 环境中运行
 */
@SpringBootTest
@Disabled("需要 Docker Compose 数据库环境（MySQL on localhost:3307）")
class NovelTranslatorApplicationTests {

    @Test
    void contextLoads() {
    }

}
