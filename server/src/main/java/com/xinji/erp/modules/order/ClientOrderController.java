package com.xinji.erp.modules.order;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xinji.erp.common.AuthContext;
import com.xinji.erp.common.PageResult;
import com.xinji.erp.common.Result;
import com.xinji.erp.modules.order.dto.CreateOrderRequest;
import com.xinji.erp.modules.order.entity.Order;
import com.xinji.erp.modules.order.vo.OrderVO;
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

@Tag(name = "03 客户端 · 订单")
@RestController
@RequestMapping("/api/client/orders")
@RequiredArgsConstructor
public class ClientOrderController {

    private final OrderService orderService;

    @Operation(summary = "提交订单（提交即冻结库存）")
    @PostMapping
    public Result<OrderVO> create(@Validated @RequestBody CreateOrderRequest req) {
        return Result.ok(orderService.create(req));
    }

    @Operation(summary = "我的订单（只能看到自己的）")
    @GetMapping
    public Result<PageResult<Order>> list(@RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "10") long size) {
        Long customerId = AuthContext.customerId();
        IPage<Order> p = orderService.pageOrders(status, customerId, null, page, size);
        return Result.ok(PageResult.of(p.getRecords(), p.getTotal(), page, size));
    }

    @Operation(summary = "订单详情（含明细）")
    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        return Result.ok(orderService.detail(orderNo));
    }

    @Operation(summary = "取消订单（释放冻结库存）")
    @PostMapping("/{orderNo}/cancel")
    public Result<Void> cancel(@PathVariable String orderNo,
                               @RequestParam(defaultValue = "客户主动取消") String reason) {
        orderService.cancel(orderNo, reason);
        return Result.ok();
    }
}
