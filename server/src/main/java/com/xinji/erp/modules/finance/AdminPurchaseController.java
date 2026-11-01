package com.xinji.erp.modules.finance;

import com.xinji.erp.common.Result;
import com.xinji.erp.modules.finance.dto.PurchaseArriveRequest;
import com.xinji.erp.modules.finance.dto.PurchaseCreateRequest;
import com.xinji.erp.modules.finance.entity.PurchaseOrder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "17 后台 · 采购管理")
@RestController
@RequestMapping("/api/admin/purchase")
@RequiredArgsConstructor
public class AdminPurchaseController {

    private final PurchaseService purchaseService;

    @Operation(summary = "采购单列表（可按状态筛选 PENDING/PARTIAL/DONE）")
    @GetMapping("/orders")
    public Result<List<PurchaseOrder>> list(@RequestParam(required = false) String status) {
        return Result.ok(purchaseService.list(status));
    }

    @Operation(summary = "采购建单")
    @PostMapping("/orders")
    public Result<PurchaseOrder> create(@Validated @RequestBody PurchaseCreateRequest req) {
        return Result.ok(purchaseService.create(req));
    }

    @Operation(summary = "到货登记（自动累加到货量并入库销售可售池）")
    @PostMapping("/orders/{id}/arrive")
    public Result<PurchaseOrder> arrive(@PathVariable Long id,
                                        @Validated @RequestBody PurchaseArriveRequest req) {
        return Result.ok(purchaseService.arrive(id, req));
    }

    @Operation(summary = "采购单详情")
    @GetMapping("/orders/{id}")
    public Result<PurchaseOrder> detail(@PathVariable Long id) {
        return Result.ok(purchaseService.detail(id));
    }

    @Operation(summary = "取消采购单（仅未到货可取消）")
    @PostMapping("/orders/{id}/cancel")
    public Result<PurchaseOrder> cancel(@PathVariable Long id,
                                        @RequestParam(required = false) String reason) {
        return Result.ok(purchaseService.cancel(id, reason));
    }
}
