package com.xinji.erp.modules.order;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinji.erp.common.AuthContext;
import com.xinji.erp.common.BizException;
import com.xinji.erp.common.IdGen;
import com.xinji.erp.common.OrderStatus;
import com.xinji.erp.common.UserPrincipal;
import com.xinji.erp.modules.auth.entity.Customer;
import com.xinji.erp.modules.auth.mapper.CustomerMapper;
import com.xinji.erp.modules.order.dto.CreateOrderRequest;
import com.xinji.erp.modules.order.dto.OrderItemRequest;
import com.xinji.erp.modules.order.entity.Order;
import com.xinji.erp.modules.order.entity.OrderItem;
import com.xinji.erp.modules.order.mapper.OrderItemMapper;
import com.xinji.erp.modules.order.mapper.OrderMapper;
import com.xinji.erp.modules.order.vo.OrderVO;
import com.xinji.erp.modules.product.entity.Sku;
import com.xinji.erp.modules.stock.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单服务。
 * 核心动作只有一件：把「下单」和「锁库存」放在同一个事务里，
 * 库存用条件更新（stock_qty >= qty）兜住并发，避免超卖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CustomerMapper customerMapper;
    private final StockService stockService;

    @Value("${xinji.order.min-qty:1}")
    private BigDecimal minQty;

    @Transactional(rollbackFor = Exception.class)
    public OrderVO create(CreateOrderRequest req) {
        UserPrincipal p = AuthContext.require();
        Long customerId = p.isEmployee()
                ? (req.getCustomerId() == null ? p.getCustomerId() : req.getCustomerId())
                : p.getCustomerId();
        if (customerId == null) {
            throw new BizException("请先选择下单客户");
        }
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw BizException.notFound("客户不存在");
        }
        if (customer.getStatus() != null && customer.getStatus() == 0) {
            throw new BizException("该客户已停用，无法下单");
        }

        String orderNo = IdGen.orderNo();
        List<OrderItem> items = new ArrayList<>();
        List<Long> touchedSkus = new ArrayList<>();
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequest ir : req.getItems()) {
            Sku sku = stockService.requireSku(ir.getSkuId());
            if (sku.getStatus() != null && sku.getStatus() == 0) {
                throw new BizException("物料「" + sku.getSkuName() + "」已下架");
            }
            BigDecimal qty = ir.getQty();
            if (minQty != null && qty.compareTo(minQty) < 0) {
                throw new BizException("单笔起订量为 " + minQty.stripTrailingZeros().toPlainString() + " 吨");
            }
            BigDecimal before = nz(sku.getStockQty());
            if (!stockService.freeze(sku.getId(), qty)) {
                throw new BizException("「" + sku.getSkuName() + "」可售库存不足，当前可售 "
                        + before.stripTrailingZeros().toPlainString() + " 吨，本次需要 "
                        + qty.stripTrailingZeros().toPlainString() + " 吨");
            }
            stockService.recordFlow(sku, "FREEZE", qty.negate(), before, before.subtract(qty),
                    orderNo, "下单冻结库存");
            touchedSkus.add(sku.getId());

            BigDecimal price = sku.getPrice();
            BigDecimal amount = price.multiply(qty).setScale(2, BigDecimal.ROUND_HALF_UP);

            OrderItem item = new OrderItem();
            item.setOrderNo(orderNo);
            item.setSkuId(sku.getId());
            item.setSkuName(sku.getSkuName());
            item.setSpec(sku.getSpec());
            item.setQty(qty);
            item.setPrice(price);
            item.setAmount(amount);
            item.setSettleQty(BigDecimal.ZERO);
            item.setSettleAmount(BigDecimal.ZERO);
            items.add(item);

            totalQty = totalQty.add(qty);
            totalAmount = totalAmount.add(amount);
        }

        BigDecimal discount = calcDiscount(totalAmount);
        BigDecimal payAmount = totalAmount.subtract(discount);

        // 授信校验：已用额度 + 本单应付 > 授信额度则拦截
        BigDecimal limit = nz(customer.getCreditLimit());
        BigDecimal used = nz(customer.getCreditUsed());
        if (limit.compareTo(BigDecimal.ZERO) > 0 && used.add(payAmount).compareTo(limit) > 0) {
            throw new BizException("超出客户授信额度：额度 "
                    + limit.stripTrailingZeros().toPlainString() + " 元，已用 "
                    + used.stripTrailingZeros().toPlainString() + " 元，本单 "
                    + payAmount.stripTrailingZeros().toPlainString() + " 元");
        }

        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setCustomerId(customer.getId());
        order.setCustomerName(customer.getCustomerName());
        order.setOrderType(req.getSeckillToken() == null ? "NORMAL" : "SECKILL");
        order.setStatus(OrderStatus.PENDING_AUDIT.name());
        order.setTotalQty(totalQty);
        order.setTotalAmount(totalAmount);
        order.setFreight(BigDecimal.ZERO);
        order.setDiscountAmount(discount);
        order.setPayAmount(payAmount);
        order.setDeliveryType(req.getDeliveryType());
        order.setExpectedDate(req.getExpectedDate());
        order.setAddress(req.getAddress() == null ? customer.getAddress() : req.getAddress());
        order.setReceiver(req.getReceiver() == null ? customer.getContactName() : req.getReceiver());
        order.setSeckillToken(req.getSeckillToken());
        order.setRemark(req.getRemark());
        orderMapper.insert(order);

        for (OrderItem item : items) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }

        // 占用授信
        customer.setCreditUsed(used.add(payAmount));
        customerMapper.updateById(customer);

        log.info("下单成功: {} 客户={} 吨数={} 金额={}", orderNo, customer.getCustomerName(), totalQty, payAmount);
        evictAfterCommit(touchedSkus);
        return detail(orderNo);
    }

    /**
     * 秒杀令牌核销下单：活动库存已在抢购时扣过，这里不再冻结销售库存，
     * 价格取令牌上的活动价。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createFromToken(com.xinji.erp.modules.seckill.entity.SeckillToken token,
                                   CreateOrderRequest req) {
        Customer customer = customerMapper.selectById(token.getCustomerId() == null
                ? AuthContext.customerId() : token.getCustomerId());
        if (customer == null) {
            throw BizException.notFound("客户不存在");
        }
        Sku sku = stockService.requireSku(token.getSkuId());
        String orderNo = IdGen.orderNo();
        BigDecimal amount = token.getPrice().multiply(token.getQty())
                .setScale(2, BigDecimal.ROUND_HALF_UP);

        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setCustomerId(customer.getId());
        order.setCustomerName(customer.getCustomerName());
        order.setOrderType("SECKILL");
        order.setStatus(OrderStatus.PENDING_AUDIT.name());
        order.setTotalQty(token.getQty());
        order.setTotalAmount(amount);
        order.setFreight(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setPayAmount(amount);
        order.setDeliveryType(req.getDeliveryType());
        order.setExpectedDate(req.getExpectedDate());
        order.setAddress(req.getAddress() == null ? customer.getAddress() : req.getAddress());
        order.setReceiver(req.getReceiver() == null ? customer.getContactName() : req.getReceiver());
        order.setSeckillToken(token.getTokenNo());
        order.setRemark(req.getRemark());
        orderMapper.insert(order);

        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setOrderNo(orderNo);
        item.setSkuId(sku.getId());
        item.setSkuName(sku.getSkuName());
        item.setSpec(sku.getSpec());
        item.setQty(token.getQty());
        item.setPrice(token.getPrice());
        item.setAmount(amount);
        item.setSettleQty(BigDecimal.ZERO);
        item.setSettleAmount(BigDecimal.ZERO);
        orderItemMapper.insert(item);

        Customer c = customerMapper.selectById(customer.getId());
        c.setCreditUsed(nz(c.getCreditUsed()).add(amount));
        customerMapper.updateById(c);

        log.info("秒杀令牌核销下单: token={} order={} 金额={}", token.getTokenNo(), orderNo, amount);
        return detail(orderNo);
    }

    /** 满 5000 减 200，演示用的简单优惠规则 */
    private BigDecimal calcDiscount(BigDecimal amount) {
        if (amount.compareTo(new BigDecimal("5000")) >= 0) {
            return new BigDecimal("200");
        }
        return BigDecimal.ZERO;
    }

    public IPage<Order> pageOrders(String status, Long customerId, String keyword, long page, long size) {
        return orderMapper.selectPage(new Page<>(page, size),
                Wrappers.<Order>lambdaQuery()
                        .eq(status != null && !status.isEmpty(), Order::getStatus, status)
                        .eq(customerId != null, Order::getCustomerId, customerId)
                        .like(keyword != null && !keyword.isEmpty(), Order::getOrderNo, keyword)
                        .orderByDesc(Order::getId));
    }

    public OrderVO detail(String orderNo) {
        Order order = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw BizException.notFound("订单不存在: " + orderNo);
        }
        List<OrderItem> items = orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, order.getId()));
        OrderVO vo = new OrderVO();
        vo.setOrder(order);
        vo.setItems(items);
        vo.setStatusLabel(OrderStatus.labelOf(order.getStatus()));
        return vo;
    }

    /** 状态流转：审核 / 派车 / 出厂 / 签收 / 对账 / 开票 / 回款 */
    @Transactional(rollbackFor = Exception.class)
    public void transfer(String orderNo, OrderStatus target) {
        Order order = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw BizException.notFound("订单不存在: " + orderNo);
        }
        String from = order.getStatus();
        if (!OrderStatus.canTransfer(from, target.name())) {
            throw new BizException("订单当前状态为「" + OrderStatus.labelOf(from)
                    + "」，不能流转到「" + target.getLabel() + "」");
        }
        order.setStatus(target.name());
        orderMapper.updateById(order);

        // 出厂：冻结量核销为实际出库（可售池在下单时已扣，这里只核销冻结，物理库存同步减少）
        if (target == OrderStatus.SHIPPED) {
            List<OrderItem> items = orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                    .eq(OrderItem::getOrderId, order.getId()));
            List<Long> touched = new ArrayList<>();
            for (OrderItem it : items) {
                Sku sku = stockService.requireSku(it.getSkuId());
                BigDecimal before = nz(sku.getStockQty());
                stockService.consumeFrozen(it.getSkuId(), it.getQty());
                stockService.recordFlow(sku, "OUTBOUND", it.getQty().negate(), before, before,
                        orderNo, "出厂核销冻结 " + it.getQty().stripTrailingZeros().toPlainString() + " 吨，物理库存减少");
                touched.add(it.getSkuId());
            }
            // 冻结量变了，商品缓存必须失效，否则客户端还看到旧的冻结数
            evictAfterCommit(touched);
        }

        // 签收：没有过磅单的明细按订单吨数兜底结算，否则结算吨数会留 0 进报表与对账
        if (target == OrderStatus.SIGNED) {
            List<OrderItem> items = orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                    .eq(OrderItem::getOrderId, order.getId()));
            for (OrderItem it : items) {
                if (it.getSettleQty() == null || it.getSettleQty().compareTo(BigDecimal.ZERO) <= 0) {
                    it.setSettleQty(it.getQty());
                    it.setSettleAmount(it.getAmount());
                    orderItemMapper.updateById(it);
                    log.info("签收兜底结算: {} 物料 {} 未过磅，按订单吨数 {} 结算",
                            orderNo, it.getSkuName(), it.getQty().stripTrailingZeros().toPlainString());
                }
            }
        }
        log.info("订单流转: {} {} -> {}", orderNo, OrderStatus.labelOf(from), target.getLabel());
    }

    /** 取消订单：释放冻结库存与授信 */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(String orderNo, String reason) {
        Order order = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw BizException.notFound("订单不存在: " + orderNo);
        }
        if (!OrderStatus.canTransfer(order.getStatus(), OrderStatus.CANCELLED.name())) {
            throw new BizException("当前状态不允许取消");
        }
        List<OrderItem> items = orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, order.getId()));
        List<Long> touched = new ArrayList<>();
        for (OrderItem it : items) {
            Sku sku = stockService.requireSku(it.getSkuId());
            BigDecimal before = nz(sku.getStockQty());
            stockService.unfreeze(it.getSkuId(), it.getQty());
            stockService.recordFlow(sku, "UNFREEZE", it.getQty(), before, before.add(it.getQty()),
                    orderNo, "订单取消，解冻库存");
            touched.add(it.getSkuId());
        }
        evictAfterCommit(touched);
        order.setStatus(OrderStatus.CANCELLED.name());
        order.setRemark((order.getRemark() == null ? "" : order.getRemark() + " | ") + "取消原因：" + reason);
        orderMapper.updateById(order);

        Customer c = customerMapper.selectById(order.getCustomerId());
        if (c != null) {
            BigDecimal used = nz(c.getCreditUsed()).subtract(nz(order.getPayAmount()));
            c.setCreditUsed(used.max(BigDecimal.ZERO));
            customerMapper.updateById(c);
        }
        log.info("订单取消: {} 原因={}", orderNo, reason);
    }

    /** 签收后回填结算吨数（由磅单汇总而来） */
    @Transactional(rollbackFor = Exception.class)
    public void applySettleQty(String orderNo, Long skuId, BigDecimal settleQty, BigDecimal settleAmount) {
        OrderItem item = orderItemMapper.selectOne(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderNo, orderNo)
                .eq(OrderItem::getSkuId, skuId)
                .last("limit 1"));
        if (item == null) {
            return;
        }
        item.setSettleQty(nz(item.getSettleQty()).add(settleQty));
        item.setSettleAmount(nz(item.getSettleAmount()).add(settleAmount));
        orderItemMapper.updateById(item);
    }

    /** 库存变了就清缓存；有事务时等提交后再清，避免并发读把未提交的旧值回填 */
    private void evictAfterCommit(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return;
        }
        skuIds.forEach(stockService::evictSkuAfterCommit);
    }

    public long countToday() {        return orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .ge(Order::getCreatedAt, LocalDateTime.now().withHour(0).withMinute(0).withSecond(0)));
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
