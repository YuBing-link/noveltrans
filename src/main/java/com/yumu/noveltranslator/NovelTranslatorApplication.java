package com.yumu.noveltranslator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NovelTranslatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(NovelTranslatorApplication.class, args);
    }

}