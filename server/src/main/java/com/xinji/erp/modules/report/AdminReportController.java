package com.xinji.erp.modules.report;

import com.xinji.erp.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "18 后台 · 报表中心")
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;

    @Operation(summary = "经营总览（本月销售 + 排行 + 账龄 + 秒杀效果）")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(reportService.overview(from, to));
    }

    @Operation(summary = "销售日报")
    @GetMapping("/sales/daily")
    public Result<List<Map<String, Object>>> salesDaily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(reportService.salesDaily(from, to));
    }

    @Operation(summary = "客户销售排行")
    @GetMapping("/sales/customers")
    public Result<List<Map<String, Object>>> customerRanking(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(reportService.customerRanking(from, to, limit));
    }

    @Operation(summary = "物料销售排行")
    @GetMapping("/sales/skus")
    public Result<List<Map<String, Object>>> skuRanking(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(reportService.skuRanking(from, to));
    }

    @Operation(summary = "出入库汇总")
    @GetMapping("/stock/flows")
    public Result<List<Map<String, Object>>> stockFlow(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(reportService.stockFlow(from, to));
    }

    @Operation(summary = "应收账龄分析")
    @GetMapping("/receivable/aging")
    public Result<List<Map<String, Object>>> aging() {
        return Result.ok(reportService.receivableAging());
    }

    @Operation(summary = "订单状态分布")
    @GetMapping("/orders/status")
    public Result<List<Map<String, Object>>> orderStatus() {
        return Result.ok(reportService.orderStatus());
    }

    @Operation(summary = "秒杀活动效果")
    @GetMapping("/seckill/effect")
    public Result<List<Map<String, Object>>> seckillEffect() {
        return Result.ok(reportService.seckillEffect());
    }
}
