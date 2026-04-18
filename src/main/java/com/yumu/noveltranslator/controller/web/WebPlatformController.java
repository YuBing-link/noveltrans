package com.yumu.noveltranslator.controller.web;

import com.yumu.noveltranslator.dto.PlatformStatsResponse;
import com.yumu.noveltranslator.dto.Result;
import com.yumu.noveltranslator.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform")
@RequiredArgsConstructor
public class WebPlatformController {

    private final UserService userService;

    @GetMapping("/stats")
    public Result<PlatformStatsResponse> getPlatformStats() {
        PlatformStatsResponse stats = userService.getPlatformStats();
        return Result.ok(stats);
    }
}
