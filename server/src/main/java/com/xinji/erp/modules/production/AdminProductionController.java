package com.xinji.erp.modules.production;

import com.xinji.erp.common.Result;
import com.xinji.erp.modules.production.dto.ProductionCreateRequest;
import com.xinji.erp.modules.production.dto.ProductionFinishRequest;
import com.xinji.erp.modules.production.entity.ProductionOrder;
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
import java.util.Map;

@Tag(name = "12 后台 · 生产管理")
@RestController
@RequestMapping("/api/admin/production")
@RequiredArgsConstructor
public class AdminProductionController {

    private final ProductionService productionService;

    @Operation(summary = "生产工单列表")
    @GetMapping("/orders")
    public Result<List<ProductionOrder>> list(@RequestParam(required = false) String status,
                                              @RequestParam(required = false) String keyword) {
        return Result.ok(productionService.list(status, keyword));
    }

    @Operation(summary = "创建生产工单")
    @PostMapping("/orders")
    public Result<ProductionOrder> create(@Validated @RequestBody ProductionCreateRequest req) {
        return Result.ok(productionService.create(req));
    }

    @Operation(summary = "开工")
    @PostMapping("/orders/{id}/start")
    public Result<ProductionOrder> start(@PathVariable Long id) {
        return Result.ok(productionService.start(id));
    }

    @Operation(summary = "完工报产（成品入销售可售池）")
    @PostMapping("/orders/{id}/finish")
    public Result<ProductionOrder> finish(@PathVariable Long id,
                                          @Validated @RequestBody ProductionFinishRequest req) {
        return Result.ok(productionService.finish(id, req));
    }

    @Operation(summary = "取消工单")
    @PostMapping("/orders/{id}/cancel")
    public Result<ProductionOrder> cancel(@PathVariable Long id,
                                          @RequestParam(required = false) String reason) {
        return Result.ok(productionService.cancel(id, reason));
    }

    @Operation(summary = "生产看板（在产数 / 本月产量 / 成品率）")
    @GetMapping("/board")
    public Result<Map<String, Object>> board() {
        return Result.ok(productionService.board());
    }
}
