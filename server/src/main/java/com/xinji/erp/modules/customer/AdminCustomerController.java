package com.xinji.erp.modules.customer;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xinji.erp.common.PageResult;
import com.xinji.erp.common.Result;
import com.xinji.erp.modules.auth.entity.Customer;
import com.xinji.erp.modules.customer.dto.CustomerRequest;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "11 后台 · 客户与授信")
@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
public class AdminCustomerController {

    private final CustomerService customerService;

    @Operation(summary = "客户分页")
    @GetMapping
    public Result<PageResult<Customer>> page(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Integer status,
                                             @RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size) {
        IPage<Customer> p = customerService.page(keyword, status, page, size);
        return Result.ok(PageResult.of(p.getRecords(), p.getTotal(), page, size));
    }

    @Operation(summary = "客户下拉列表")
    @GetMapping("/options")
    public Result<List<Customer>> options() {
        return Result.ok(customerService.list());
    }

    @Operation(summary = "客户详情（含授信占用）")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(customerService.detail(id));
    }

    @Operation(summary = "新增客户")
    @PostMapping
    public Result<Customer> create(@Validated @RequestBody CustomerRequest req) {
        return Result.ok(customerService.save(null, req));
    }

    @Operation(summary = "修改客户")
    @PutMapping("/{id}")
    public Result<Customer> update(@PathVariable Long id, @Validated @RequestBody CustomerRequest req) {
        return Result.ok(customerService.save(id, req));
    }

    @Operation(summary = "调整授信额度")
    @PostMapping("/{id}/credit")
    public Result<Customer> credit(@PathVariable Long id, @RequestParam BigDecimal creditLimit) {
        return Result.ok(customerService.adjustCredit(id, creditLimit));
    }

    @Operation(summary = "启用 / 停用客户")
    @PostMapping("/{id}/status")
    public Result<Customer> toggle(@PathVariable Long id,
                                   @RequestParam(required = false) Integer status) {
        return Result.ok(customerService.toggle(id, status));
    }

    @Operation(summary = "授信预警（已用超 80%）")
    @GetMapping("/credit-warnings")
    public Result<List<Map<String, Object>>> creditWarnings() {
        return Result.ok(customerService.creditWarnings());
    }
}
