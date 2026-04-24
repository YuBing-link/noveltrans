package com.yumu.noveltranslator;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NovelTranslatorApplication {

    static {
        // 加载 .env 文件（如果存在），将值注入到系统环境变量中
        try {
            Dotenv dotenv = Dotenv.configure()
                .directory(".")
                .ignoreIfMissing()
                .load();
            dotenv.entries().forEach(entry -> {
                if (System.getenv(entry.getKey()) == null) {
                    setEnv(entry.getKey(), entry.getValue());
                }
            });
        } catch (Exception e) {
            // 忽略 .env 不存在的错误
        }
    }

    @SuppressWarnings("unchecked")
    private static void setEnv(String key, String value) {
        try {
            // Windows 不支持直接修改环境变量，通过 ProcessEnvironment 反射注入
            var env = System.getenv();
            if (env instanceof java.util.Map<?, ?> envMap) {
                var envsField = env.getClass().getDeclaredField("m");
                envsField.setAccessible(true);
                ((java.util.Map<String, String>) envMap).put(key, value);
            }
        } catch (Exception e) {
            // 静默失败
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(NovelTranslatorApplication.class, args);
    }

}