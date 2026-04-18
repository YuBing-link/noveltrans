package com.yumu.noveltranslator.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebVerificationController {

    @GetMapping("/verification")
    public String verificationPage() {
        return "verification";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "verification";
    }
}
