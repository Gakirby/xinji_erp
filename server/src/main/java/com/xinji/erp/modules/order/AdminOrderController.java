package com.xinji.erp.modules.order;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xinji.erp.common.OrderStatus;
import com.xinji.erp.common.PageResult;
import com.xinji.erp.common.Result;
import com.xinji.erp.modules.order.entity.Order;
import com.xinji.erp.modules.order.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "12 后台 · 订单中心")
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    @Operation(summary = "订单分页查询")
    @GetMapping
    public Result<PageResult<Order>> list(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) Long customerId,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "20") long size) {
        IPage<Order> p = orderService.pageOrders(status, customerId, keyword, page, size);
        return Result.ok(PageResult.of(p.getRecords(), p.getTotal(), page, size));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        return Result.ok(orderService.detail(orderNo));
    }

    @Operation(summary = "审核通过")
    @PostMapping("/{orderNo}/audit")
    public Result<Void> audit(@PathVariable String orderNo) {
        orderService.transfer(orderNo, OrderStatus.AUDITED);
        return Result.ok();
    }

    @Operation(summary = "派车")
    @PostMapping("/{orderNo}/dispatch")
    public Result<Void> dispatch(@PathVariable String orderNo) {
        orderService.transfer(orderNo, OrderStatus.DISPATCHED);
        return Result.ok();
    }

    @Operation(summary = "过磅出厂（核销冻结库存）")
    @PostMapping("/{orderNo}/ship")
    public Result<Void> ship(@PathVariable String orderNo) {
        orderService.transfer(orderNo, OrderStatus.SHIPPED);
        return Result.ok();
    }

    @Operation(summary = "客户签收")
    @PostMapping("/{orderNo}/sign")
    public Result<Void> sign(@PathVariable String orderNo) {
        orderService.transfer(orderNo, OrderStatus.SIGNED);
        return Result.ok();
    }

    @Operation(summary = "取消订单")
    @PostMapping("/{orderNo}/cancel")
    public Result<Void> cancel(@PathVariable String orderNo,
                               @RequestParam(defaultValue = "后台取消") String reason) {
        orderService.cancel(orderNo, reason);
        return Result.ok();
    }
}
