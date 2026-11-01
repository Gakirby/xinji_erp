package com.xinji.erp.modules.product;

import com.xinji.erp.common.Result;
import com.xinji.erp.modules.product.dto.SkuRequest;
import com.xinji.erp.modules.product.dto.WarehouseRequest;
import com.xinji.erp.modules.product.entity.Sku;
import com.xinji.erp.modules.product.entity.Warehouse;
import com.xinji.erp.modules.product.vo.SkuVO;
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

import java.util.List;
import java.util.Map;

@Tag(name = "10 后台 · 物料档案与库存看板")
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    @Operation(summary = "物料列表（含三个库存口径）")
    @GetMapping
    public Result<List<SkuVO>> list() {
        return Result.ok(productService.listForAdmin());
    }

    @Operation(summary = "库存预警")
    @GetMapping("/warnings")
    public Result<List<Map<String, Object>>> warnings() {
        return Result.ok(productService.warningList());
    }

    @Operation(summary = "新增物料（可带期初库存）")
    @PostMapping
    public Result<Sku> create(@Validated @RequestBody SkuRequest req) {
        return Result.ok(productService.saveSku(null, req));
    }

    @Operation(summary = "修改物料")
    @PutMapping("/{id}")
    public Result<Sku> update(@PathVariable Long id, @Validated @RequestBody SkuRequest req) {
        return Result.ok(productService.saveSku(id, req));
    }

    @Operation(summary = "上架 / 下架")
    @PostMapping("/{id}/status")
    public Result<Sku> toggle(@PathVariable Long id,
                              @RequestParam(required = false) Integer status) {
        return Result.ok(productService.toggleSku(id, status));
    }

    @Operation(summary = "料仓列表")
    @GetMapping("/warehouses")
    public Result<List<Warehouse>> warehouses() {
        return Result.ok(productService.warehouses());
    }

    @Operation(summary = "新增料仓")
    @PostMapping("/warehouses")
    public Result<Warehouse> createWarehouse(@Validated @RequestBody WarehouseRequest req) {
        return Result.ok(productService.saveWarehouse(req));
    }
}
