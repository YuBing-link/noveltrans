package com.yumu.noveltranslator.adapter.in.rest.web;

import com.yumu.noveltranslator.dto.entity.PlatformStatsResponse;
import com.yumu.noveltranslator.dto.common.Result;
import com.yumu.noveltranslator.domain.service.UserService;
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
