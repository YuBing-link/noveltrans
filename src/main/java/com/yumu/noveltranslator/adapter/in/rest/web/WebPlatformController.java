package com.yumu.noveltranslator.adapter.in.rest.web;

import com.yumu.noveltranslator.port.dto.entity.PlatformStatsResponse;
import com.yumu.noveltranslator.port.dto.common.Result;
import com.yumu.noveltranslator.port.in.UserPort;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform")
@RequiredArgsConstructor
public class WebPlatformController {

    private final UserPort userPort;

    @GetMapping("/stats")
    public Result<PlatformStatsResponse> getPlatformStats() {
        PlatformStatsResponse stats = userPort.getPlatformStats();
        return Result.ok(stats);
    }
}
