package com.yumu.noveltranslator.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "index"; // 返回主页
    }

    @GetMapping("/home")
    public String homePage() {
        return "index";
    }
}