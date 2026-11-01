package com.xinji.erp.modules.auth;

import com.xinji.erp.common.Result;
import com.xinji.erp.modules.auth.dto.LoginResponse;
import com.xinji.erp.modules.auth.dto.RegisterRequest;
import com.xinji.erp.modules.auth.dto.SendSmsCodeRequest;
import com.xinji.erp.modules.auth.dto.SendSmsCodeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户自助注册。挂在 /api/public 下，属于登录前可访问的白名单。
 */
@Tag(name = "02 客户注册")
@RestController
@RequestMapping("/api/public/auth")
@RequiredArgsConstructor
public class PublicAuthController {

    private final RegisterService registerService;

    @Operation(summary = "发送注册短信验证码（60 秒频控，5 分钟有效）")
    @PostMapping("/sms-code")
    public Result<SendSmsCodeResponse> sendCode(@Validated @RequestBody SendSmsCodeRequest req) {
        return Result.ok(registerService.sendCode(req.getMobile()));
    }

    @Operation(summary = "手机号注册：建客户档案 + 登录账号，成功后直接返回 token")
    @PostMapping("/register")
    public Result<LoginResponse> register(@Validated @RequestBody RegisterRequest req) {
        return Result.ok(registerService.register(req));
    }

    @Operation(summary = "手机号是否已注册（注册页输入框失焦时预校验）")
    @GetMapping("/mobile-check")
    public Result<Boolean> mobileCheck(@RequestParam String mobile) {
        return Result.ok(registerService.isRegistered(mobile));
    }
}
