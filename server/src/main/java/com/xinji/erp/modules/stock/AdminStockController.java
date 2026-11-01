package com.xinji.erp.modules.stock;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xinji.erp.common.PageResult;
import com.xinji.erp.common.Result;
import com.xinji.erp.modules.stock.dto.StockOpRequest;
import com.xinji.erp.modules.stock.entity.StockFlow;
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

import java.util.List;
import java.util.Map;

@Tag(name = "11 后台 · 仓储与库存")
@RestController
@RequestMapping("/api/admin/stock")
@RequiredArgsConstructor
public class AdminStockController {

    private final StockService stockService;

    @Operation(summary = "库存流水（可按物料、类型筛选）")
    @GetMapping("/flows")
    public Result<PageResult<StockFlow>> flows(@RequestParam(required = false) Long skuId,
                                               @RequestParam(required = false) String bizType,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "20") long size) {
        IPage<StockFlow> p = stockService.pageFlow(skuId, bizType, page, size);
        return Result.ok(PageResult.of(p.getRecords(), p.getTotal(), page, size));
    }

    @Operation(summary = "按业务类型汇总库存变动")
    @GetMapping("/summary")
    public Result<List<Map<String, Object>>> summary() {
        return Result.ok(stockService.summaryByBizType());
    }

    @Operation(summary = "划拨：销售可售池 → 活动库存池")
    @PostMapping("/alloc")
    public Result<Void> alloc(@Validated @RequestBody StockOpRequest req) {
        stockService.allocToActivity(req);
        return Result.ok();
    }

    @Operation(summary = "回拨：活动库存池 → 销售可售池")
    @PostMapping("/return")
    public Result<Void> returnBack(@Validated @RequestBody StockOpRequest req) {
        stockService.returnFromActivity(req);
        return Result.ok();
    }

    @Operation(summary = "采购入库")
    @PostMapping("/inbound")
    public Result<Void> inbound(@Validated @RequestBody StockOpRequest req) {
        stockService.inbound(req);
        return Result.ok();
    }
}
