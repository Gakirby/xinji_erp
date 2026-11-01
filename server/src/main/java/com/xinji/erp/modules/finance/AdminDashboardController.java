package com.xinji.erp.modules.finance;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xinji.erp.common.OrderStatus;
import com.xinji.erp.common.Result;
import com.xinji.erp.modules.dispatch.entity.DispatchOrder;
import com.xinji.erp.modules.dispatch.mapper.DispatchOrderMapper;
import com.xinji.erp.modules.finance.entity.Invoice;
import com.xinji.erp.modules.finance.mapper.InvoiceMapper;
import com.xinji.erp.modules.finance.mapper.PaymentMapper;
import com.xinji.erp.modules.order.entity.Order;
import com.xinji.erp.modules.order.mapper.OrderMapper;
import com.xinji.erp.modules.product.ProductService;
import com.xinji.erp.modules.product.vo.SkuVO;
import com.xinji.erp.modules.production.entity.ProductionOrder;
import com.xinji.erp.modules.production.mapper.ProductionOrderMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "20 后台 · 工作台")
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final OrderMapper orderMapper;
    private final ProductService productService;
    private final FinanceService financeService;
    private final DispatchOrderMapper dispatchMapper;
    private final ProductionOrderMapper productionMapper;
    private final InvoiceMapper invoiceMapper;
    private final PaymentMapper paymentMapper;

    @Operation(summary = "工作台 KPI 与分布")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        Map<String, Object> m = new HashMap<>();

        List<Map<String, Object>> byStatus = orderMapper.countByStatus();
        long totalOrders = 0;
        Map<String, Long> statusMap = new HashMap<>();
        for (Map<String, Object> row : byStatus) {
            String st = String.valueOf(row.get("status"));
            long cnt = ((Number) row.get("cnt")).longValue();
            statusMap.put(st, cnt);
            totalOrders += cnt;
        }
        m.put("totalOrders", totalOrders);
        m.put("statusDistribution", statusMap);
        m.put("pendingAudit", statusMap.getOrDefault(OrderStatus.PENDING_AUDIT.name(), 0L));
        m.put("pendingDispatch", statusMap.getOrDefault(OrderStatus.AUDITED.name(), 0L));
        m.put("inTransit", statusMap.getOrDefault(OrderStatus.SHIPPED.name(), 0L));

        BigDecimal outstanding = financeService.outstanding();
        m.put("receivableOutstanding", outstanding == null ? BigDecimal.ZERO : outstanding);

        List<SkuVO> skus = productService.listForAdmin();
        BigDecimal totalStock = BigDecimal.ZERO;
        BigDecimal totalSaleable = BigDecimal.ZERO;
        BigDecimal totalActivity = BigDecimal.ZERO;
        for (SkuVO v : skus) {
            totalStock = totalStock.add(v.getTotalQty());
            totalSaleable = totalSaleable.add(v.getSaleableQty());
            totalActivity = totalActivity.add(v.getActivityAllocQty());
        }
        m.put("totalStock", totalStock);
        m.put("totalSaleable", totalSaleable);
        m.put("totalActivityAlloc", totalActivity);
        m.put("skuCount", skus.size());
        m.put("warnings", productService.warningList());

        // 调度 / 生产 / 开票：三个新模块的待办数量，工作台一屏看完
        m.put("dispatchingCount", dispatchMapper.selectCount(Wrappers.<DispatchOrder>lambdaQuery()
                .in(DispatchOrder::getStatus, DispatchOrder.PENDING, DispatchOrder.LOADING)));
        m.put("producingCount", productionMapper.selectCount(Wrappers.<ProductionOrder>lambdaQuery()
                .eq(ProductionOrder::getStatus, ProductionOrder.RUNNING)));
        m.put("pendingInvoiceCount", invoiceMapper.selectCount(Wrappers.<Invoice>lambdaQuery()
                .eq(Invoice::getStatus, Invoice.PENDING)));
        BigDecimal monthReceived = paymentMapper.sumThisMonth();
        m.put("monthReceived", monthReceived == null ? BigDecimal.ZERO : monthReceived);
        return Result.ok(m);
    }

    @Operation(summary = "各状态订单数量")
    @GetMapping("/orders/by-status")
    public Result<List<Map<String, Object>>> byStatus() {
        return Result.ok(orderMapper.countByStatus());
    }

    @Operation(summary = "最近订单")
    @GetMapping("/orders/recent")
    public Result<List<Order>> recentOrders() {
        return Result.ok(orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .orderByDesc(Order::getId)
                .last("limit 10")));
    }
}
