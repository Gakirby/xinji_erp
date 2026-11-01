package com.xinji.erp.modules.product;

import com.xinji.erp.common.Result;
import com.xinji.erp.modules.product.vo.SkuVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "02 客户端 · 商品")
@RestController
@RequestMapping("/api/client/products")
@RequiredArgsConstructor
public class ClientProductController {

    private final ProductService productService;

    @Operation(summary = "在售商品列表（可售量 = 库存 − 活动划拨 − 冻结）")
    @GetMapping
    public Result<List<SkuVO>> list() {
        return Result.ok(productService.listForClient());
    }

    @Operation(summary = "商品详情")
    @GetMapping("/{id}")
    public Result<SkuVO> detail(@PathVariable Long id) {
        return Result.ok(productService.detail(id, true));
    }
}
