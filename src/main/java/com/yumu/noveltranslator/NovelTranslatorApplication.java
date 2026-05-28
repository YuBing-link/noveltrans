package com.yumu.noveltranslator;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class NovelTranslatorApplication {

    static {
        // 加载 .env 文件（如果存在），将值注入为系统属性
        // Spring Boot 的 ${xxx} 占位符会从系统属性读取配置
        try {
            Dotenv dotenv = Dotenv.configure()
                .directory(".")
                .ignoreIfMissing()
                .load();
            dotenv.entries().forEach(entry -> {
                if (System.getenv(entry.getKey()) == null && System.getProperty(entry.getKey()) == null) {
                    System.setProperty(entry.getKey(), entry.getValue());
                }
            });
        } catch (Exception e) {
            // 忽略 .env 不存在的错误
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(NovelTranslatorApplication.class, args);
    }

}
