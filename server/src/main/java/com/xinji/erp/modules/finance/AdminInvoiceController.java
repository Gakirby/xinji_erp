package com.xinji.erp.modules.finance;

import com.xinji.erp.common.Result;
import com.xinji.erp.modules.finance.dto.InvoiceRequest;
import com.xinji.erp.modules.finance.entity.Invoice;
import com.xinji.erp.modules.finance.entity.Payment;
import com.xinji.erp.modules.finance.entity.Receivable;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "16 后台 · 发票与回款")
@RestController
@RequestMapping("/api/admin/finance")
@RequiredArgsConstructor
public class AdminInvoiceController {

    private final InvoiceService invoiceService;
    private final FinanceService financeService;

    // ---------------- 发票 ----------------

    @Operation(summary = "发票列表")
    @GetMapping("/invoices")
    public Result<List<Invoice>> invoices(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) Long customerId) {
        return Result.ok(invoiceService.list(status, customerId));
    }

    @Operation(summary = "申请开票（挂应收单或对账批次）")
    @PostMapping("/invoices")
    public Result<Invoice> createInvoice(@Validated @RequestBody InvoiceRequest req) {
        return Result.ok(invoiceService.create(req));
    }

    @Operation(summary = "开具发票（对账订单推进到已开票）")
    @PostMapping("/invoices/{id}/issue")
    public Result<Invoice> issue(@PathVariable Long id) {
        return Result.ok(invoiceService.issue(id));
    }

    @Operation(summary = "作废发票")
    @PostMapping("/invoices/{id}/void")
    public Result<Invoice> voidInvoice(@PathVariable Long id,
                                       @RequestParam(required = false) String reason) {
        return Result.ok(invoiceService.voidInvoice(id, reason));
    }

    @Operation(summary = "开票统计")
    @GetMapping("/invoices/stats")
    public Result<Map<String, Object>> invoiceStats() {
        return Result.ok(invoiceService.stats());
    }

    // ---------------- 回款流水 ----------------

    @Operation(summary = "收款流水列表")
    @GetMapping("/payments")
    public Result<List<Payment>> payments(@RequestParam(required = false) Long customerId,
                                          @RequestParam(required = false) Long receivableId) {
        return Result.ok(financeService.payments(customerId, receivableId));
    }

    @Operation(summary = "回款统计")
    @GetMapping("/payments/stats")
    public Result<Map<String, Object>> paymentStats() {
        return Result.ok(financeService.paymentStats());
    }

    @Operation(summary = "收款登记（带收款方式）")
    @PostMapping("/receivables/{id}/receive-detail")
    public Result<Receivable> receiveDetail(@PathVariable Long id,
                                            @RequestParam BigDecimal amount,
                                            @RequestParam(required = false) String payMethod,
                                            @RequestParam(required = false) String remark) {
        return Result.ok(financeService.receive(id, amount, payMethod, remark));
    }
}
