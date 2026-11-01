package com.xinji.erp.modules.finance;

import com.xinji.erp.common.Result;
import com.xinji.erp.modules.finance.dto.SettlementRequest;
import com.xinji.erp.modules.finance.entity.Receivable;
import com.xinji.erp.modules.finance.entity.Settlement;
import com.xinji.erp.modules.finance.entity.SettlementItem;
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

@Tag(name = "15 后台 · 财务结算")
@RestController
@RequestMapping("/api/admin/finance")
@RequiredArgsConstructor
public class AdminFinanceController {

    private final FinanceService financeService;

    @Operation(summary = "生成对账批次（把账期内已签收订单收进来）")
    @PostMapping("/settlements")
    public Result<Settlement> generate(@Validated @RequestBody SettlementRequest req) {
        return Result.ok(financeService.generate(req));
    }

    @Operation(summary = "对账批次列表")
    @GetMapping("/settlements")
    public Result<List<Settlement>> settlements() {
        return Result.ok(financeService.settlements());
    }

    @Operation(summary = "对账明细")
    @GetMapping("/settlements/{id}/items")
    public Result<List<SettlementItem>> items(@PathVariable Long id) {
        return Result.ok(financeService.settlementItems(id));
    }

    @Operation(summary = "客户确认对账并生成应收")
    @PostMapping("/settlements/{id}/confirm")
    public Result<Receivable> confirm(@PathVariable Long id) {
        return Result.ok(financeService.confirm(id));
    }

    @Operation(summary = "应收列表")
    @GetMapping("/receivables")
    public Result<List<Receivable>> receivables() {
        return Result.ok(financeService.receivables());
    }

    @Operation(summary = "收款登记")
    @PostMapping("/receivables/{id}/receive")
    public Result<Receivable> receive(@PathVariable Long id, @RequestParam BigDecimal amount) {
        return Result.ok(financeService.receive(id, amount));
    }

    @Operation(summary = "应收未收合计")
    @GetMapping("/receivables/outstanding")
    public Result<BigDecimal> outstanding() {
        return Result.ok(financeService.outstanding());
    }
}
