package com.yumu.noveltranslator.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class VerificationController {

    @GetMapping("/verification")
    public String verificationPage() {
        return "verification"; // 返回verification.html模板
    }

    @GetMapping("/register")
    public String registerPage() {
        return "verification"; // 注册页面也使用相同的验证码页面
    }
}