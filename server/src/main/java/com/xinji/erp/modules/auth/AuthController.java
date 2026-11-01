package com.xinji.erp.modules.auth;

import com.xinji.erp.common.Result;
import com.xinji.erp.modules.auth.dto.LoginRequest;
import com.xinji.erp.modules.auth.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "01 登录鉴权")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录（客户与员工共用）")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Validated @RequestBody LoginRequest req) {
        return Result.ok(authService.login(req));
    }

    @Operation(summary = "当前登录信息")
    @GetMapping("/me")
    public Result<LoginResponse> me() {
        return Result.ok(authService.current());
    }
}
