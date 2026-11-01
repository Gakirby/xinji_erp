package com.xinji.erp.modules.auth;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xinji.erp.common.PageResult;
import com.xinji.erp.common.Result;
import com.xinji.erp.modules.auth.dto.UserSaveRequest;
import com.xinji.erp.modules.auth.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "19 后台 · 账号与权限")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @Operation(summary = "账号分页（员工 / 客户）")
    @GetMapping
    public Result<PageResult<User>> page(@RequestParam(required = false) String userType,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "10") long size) {
        IPage<User> p = userService.page(userType, keyword, page, size);
        return Result.ok(PageResult.of(p.getRecords(), p.getTotal(), page, size));
    }

    @Operation(summary = "新建账号（客户账号必须绑定 customer_id）")
    @PostMapping
    public Result<User> create(@Validated @RequestBody UserSaveRequest req) {
        return Result.ok(userService.create(req));
    }

    @Operation(summary = "修改账号")
    @PutMapping("/{id}")
    public Result<User> update(@PathVariable Long id, @Validated @RequestBody UserSaveRequest req) {
        return Result.ok(userService.update(id, req));
    }

    @Operation(summary = "重置密码")
    @PostMapping("/{id}/reset-password")
    public Result<User> resetPassword(@PathVariable Long id,
                                      @RequestParam String newPassword) {
        return Result.ok(userService.resetPassword(id, newPassword));
    }

    @Operation(summary = "启用 / 停用账号")
    @PostMapping("/{id}/status")
    public Result<User> toggle(@PathVariable Long id,
                               @RequestParam(required = false) Integer status) {
        return Result.ok(userService.toggle(id, status));
    }

    @Operation(summary = "账号概览")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(userService.overview());
    }
}
