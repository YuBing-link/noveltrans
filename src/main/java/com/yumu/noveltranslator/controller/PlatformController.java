package com.yumu.noveltranslator.controller;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 平台公开接口（无需认证）
 */
@RestController
@RequestMapping("/platform")
public class PlatformController {

    @Autowired
    private UserService userService;

    /**
     * 获取平台统计信息
     * GET /platform/stats
     */
    @GetMapping("/stats")
    public Result<PlatformStatsResponse> getPlatformStats() {
        PlatformStatsResponse stats = userService.getPlatformStats();
        return Result.ok(stats, "200");
    }
}
