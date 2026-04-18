package com.yumu.noveltranslator.controller.plugin;

import com.yumu.noveltranslator.dto.Result;
import com.yumu.noveltranslator.entity.User;
import com.yumu.noveltranslator.security.CustomUserDetails;
import com.yumu.noveltranslator.service.DeviceTokenService;
import com.yumu.noveltranslator.util.JwtUtils;
import com.yumu.noveltranslator.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 插件端认证接口（设备 Token）
 * 路径前缀: /user
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class PluginAuthController {

    private final DeviceTokenService deviceTokenService;
    private final JwtUtils jwtUtils;

    /**
     * 网站登录后，注册设备 Token
     * POST /user/register-device
     */
    @PostMapping("/register-device")
    public Result<String> registerDevice(@RequestBody Map<String, String> request) {
        String deviceId = request.get("deviceId");

        if (deviceId == null || deviceId.trim().isEmpty()) {
            return Result.error("设备 ID 不能为空", "400");
        }

        CustomUserDetails userDetails = SecurityUtil.getRequiredUserDetails();
        User user = userDetails.getUser();

        String token = jwtUtils.createToken((long) user.getId(), user.getEmail());
        deviceTokenService.registerToken(deviceId, token);

        return Result.ok("设备注册成功", "200");
    }

    /**
     * 插件获取 Token（通过设备 ID）
     * GET /user/get-token/{deviceId}
     */
    @GetMapping("/get-token/{deviceId}")
    public Result<Map<String, String>> getToken(@PathVariable String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return Result.error("设备 ID 不能为空", "400");
        }

        String token = deviceTokenService.getToken(deviceId);

        if (token == null) {
            return Result.error("未找到登录信息，请先在网站登录", "404");
        }

        try {
            var decoded = jwtUtils.verifyToken(token);
            Map<String, String> result = jwtUtils.getUserInfoFromToken(token);
            result.put("token", token);
            return Result.ok(result, "200");
        } catch (Exception e) {
            deviceTokenService.removeToken(deviceId);
            return Result.error("登录已过期，请重新登录", "401");
        }
    }
}
