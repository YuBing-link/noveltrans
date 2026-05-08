package com.yumu.noveltranslator.adapter.in.rest.plugin;

import com.yumu.noveltranslator.domain.model.User;
import com.yumu.noveltranslator.port.dto.common.Result;
import com.yumu.noveltranslator.enums.ErrorCodeEnum;
import com.yumu.noveltranslator.port.in.AuthPort;
import com.yumu.noveltranslator.port.in.DeviceTokenPort;
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

    private final AuthPort authPort;
    private final DeviceTokenPort deviceTokenPort;

    /**
     * 网站登录后，注册设备 Token
     * POST /user/register-device
     */
    @PostMapping("/register-device")
    public Result<String> registerDevice(@RequestBody Map<String, String> request) {
        String deviceId = request.get("deviceId");

        if (deviceId == null || deviceId.trim().isEmpty()) {
            return Result.error(ErrorCodeEnum.PARAMETER_ERROR, "设备 ID 不能为空");
        }

        Long userId = SecurityUtil.getRequiredUserId();
        User user = authPort.getUserById(userId).orElse(null);
        if (user == null) {
            return Result.error(ErrorCodeEnum.USER_NOT_FOUND, "用户不存在");
        }

        deviceTokenPort.generateAndRegisterToken(deviceId, user.getId(), user.getEmail(), user.getTenantId());

        return Result.ok("设备注册成功", "200");
    }

    /**
     * 插件获取 Token（通过设备 ID）
     * GET /user/get-token/{deviceId}
     */
    @GetMapping("/get-token/{deviceId}")
    public Result<Map<String, String>> getToken(@PathVariable String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return Result.error(ErrorCodeEnum.PARAMETER_ERROR, "设备 ID 不能为空");
        }

        Map<String, String> result = deviceTokenPort.verifyAndGetUserInfo(deviceId);

        if (result == null) {
            return Result.error(ErrorCodeEnum.NOT_FOUND, "未找到登录信息，请先在网站登录");
        }

        return Result.ok(result, "200");
    }
}
