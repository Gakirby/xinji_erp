package com.xinji.erp.modules.finance;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xinji.erp.common.AuthContext;
import com.xinji.erp.common.BizException;
import com.xinji.erp.common.IdGen;
import com.xinji.erp.common.OrderStatus;
import com.xinji.erp.common.UserPrincipal;
import com.xinji.erp.modules.auth.entity.Customer;
import com.xinji.erp.modules.auth.mapper.CustomerMapper;
import com.xinji.erp.modules.finance.dto.SettlementRequest;
import com.xinji.erp.modules.finance.entity.Payment;
import com.xinji.erp.modules.finance.entity.Receivable;
import com.xinji.erp.modules.finance.entity.Settlement;
import com.xinji.erp.modules.finance.entity.SettlementItem;
import com.xinji.erp.modules.finance.mapper.PaymentMapper;
import com.xinji.erp.modules.finance.mapper.ReceivableMapper;
import com.xinji.erp.modules.finance.mapper.SettlementItemMapper;
import com.xinji.erp.modules.finance.mapper.SettlementMapper;
import com.xinji.erp.modules.order.entity.Order;
import com.xinji.erp.modules.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceService {

    private final SettlementMapper settlementMapper;
    private final SettlementItemMapper itemMapper;
    private final ReceivableMapper receivableMapper;
    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final CustomerMapper customerMapper;

    /** 生成对账批次：把账期内已签收、尚未对账的订单收进来 */
    @Transactional(rollbackFor = Exception.class)
    public Settlement generate(SettlementRequest req) {
        Customer customer = customerMapper.selectById(req.getCustomerId());
        if (customer == null) {
            throw BizException.notFound("客户不存在");
        }
        if (req.getPeriodEnd().isBefore(req.getPeriodStart())) {
            throw new BizException("账期结束日期不能早于开始日期");
        }
        List<Order> orders = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .eq(Order::getCustomerId, req.getCustomerId())
                .eq(Order::getStatus, OrderStatus.SIGNED.name())
                .ge(Order::getCreatedAt, req.getPeriodStart().atStartOfDay())
                .le(Order::getCreatedAt, req.getPeriodEnd().atTime(23, 59, 59)));
        if (orders.isEmpty()) {
            throw new BizException("该账期内没有可对账的已签收订单");
        }

        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Order o : orders) {
            totalQty = totalQty.add(nz(o.getTotalQty()));
            totalAmount = totalAmount.add(nz(o.getPayAmount()));
        }

        Settlement s = new Settlement();
        s.setSettlementNo(IdGen.settlementNo());
        s.setCustomerId(customer.getId());
        s.setCustomerName(customer.getCustomerName());
        s.setPeriodStart(req.getPeriodStart());
        s.setPeriodEnd(req.getPeriodEnd());
        s.setOrderCount(orders.size());
        s.setTotalQty(totalQty);
        s.setTotalAmount(totalAmount);
        s.setStatus("DRAFT");
        settlementMapper.insert(s);

        for (Order o : orders) {
            SettlementItem item = new SettlementItem();
            item.setSettlementId(s.getId());
            item.setOrderId(o.getId());
            item.setOrderNo(o.getOrderNo());
            item.setSettleQty(nz(o.getTotalQty()));
            item.setAmount(nz(o.getPayAmount()));
            item.setSettleDate(o.getCreatedAt() == null
                    ? LocalDate.now() : o.getCreatedAt().toLocalDate());
            itemMapper.insert(item);

            o.setStatus(OrderStatus.SETTLED.name());
            orderMapper.updateById(o);
        }
        log.info("生成对账批次: {} 客户={} 订单={} 金额={}",
                s.getSettlementNo(), customer.getCustomerName(), orders.size(), totalAmount);
        return s;
    }

    /** 客户确认对账 → 生成应收 */
    @Transactional(rollbackFor = Exception.class)
    public Receivable confirm(Long settlementId) {
        Settlement s = settlementMapper.selectById(settlementId);
        if (s == null) {
            throw BizException.notFound("对账批次不存在");
        }
        if (!"DRAFT".equals(s.getStatus())) {
            throw new BizException("该批次状态为「" + s.getStatus() + "」，不能重复确认");
        }
        s.setStatus("CONFIRMED");
        s.setConfirmedAt(LocalDateTime.now());
        settlementMapper.updateById(s);

        Receivable r = new Receivable();
        r.setReceivableNo(IdGen.receivableNo());
        r.setCustomerId(s.getCustomerId());
        r.setCustomerName(s.getCustomerName());
        r.setSettlementId(s.getId());
        r.setAmount(s.getTotalAmount());
        r.setReceivedAmount(BigDecimal.ZERO);
        r.setStatus("UNPAID");
        r.setDueDate(LocalDate.now().plusDays(30));
        receivableMapper.insert(r);
        log.info("对账确认并生成应收: {} 金额={}", r.getReceivableNo(), r.getAmount());
        return r;
    }

    /** 收款登记（默认网银转账） */
    @Transactional(rollbackFor = Exception.class)
    public Receivable receive(Long receivableId, BigDecimal amount) {
        return receive(receivableId, amount, "TRANSFER", null);
    }

    /**
     * 收款登记：更新应收余额 + 写一条收款流水。
     * 只累加 received_amount 不落流水的话，客户对账时说不清这笔钱是哪天到的。
     */
    @Transactional(rollbackFor = Exception.class)
    public Receivable receive(Long receivableId, BigDecimal amount, String payMethod, String remark) {
        Receivable r = receivableMapper.selectById(receivableId);
        if (r == null) {
            throw BizException.notFound("应收单不存在");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("收款金额必须大于 0");
        }
        BigDecimal received = nz(r.getReceivedAmount()).add(amount);
        if (received.compareTo(nz(r.getAmount())) > 0) {
            throw new BizException("收款金额超过应收金额，应收 " + r.getAmount() + " 元");
        }
        r.setReceivedAmount(received);
        r.setStatus(received.compareTo(nz(r.getAmount())) == 0 ? "PAID" : "PARTIAL");
        receivableMapper.updateById(r);

        Payment p = new Payment();
        p.setPaymentNo(IdGen.settlePayNo());
        p.setReceivableId(r.getId());
        p.setReceivableNo(r.getReceivableNo());
        p.setCustomerId(r.getCustomerId());
        p.setCustomerName(r.getCustomerName());
        p.setAmount(amount);
        p.setPayMethod(payMethod == null ? "TRANSFER" : payMethod);
        p.setPayTime(LocalDateTime.now());
        UserPrincipal principal = AuthContext.get();
        p.setOperator(principal == null ? "系统" : principal.getDisplayName());
        p.setRemark(remark);
        paymentMapper.insert(p);

        log.info("收款登记: {} 本次={} 累计={}/{} 方式={} 流水={}", r.getReceivableNo(), amount,
                received, r.getAmount(), p.getPayMethod(), p.getPaymentNo());
        return r;
    }

    public List<Payment> payments(Long customerId, Long receivableId) {
        return paymentMapper.selectList(Wrappers.<Payment>lambdaQuery()
                .eq(customerId != null, Payment::getCustomerId, customerId)
                .eq(receivableId != null, Payment::getReceivableId, receivableId)
                .orderByDesc(Payment::getId));
    }

    /** 回款统计 */
    public java.util.Map<String, Object> paymentStats() {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("totalReceived", paymentMapper.sumAll());
        m.put("monthReceived", paymentMapper.sumThisMonth());
        m.put("outstanding", receivableMapper.sumOutstanding());
        m.put("partialCount", receivableMapper.selectCount(Wrappers.<Receivable>lambdaQuery()
                .eq(Receivable::getStatus, "PARTIAL")));
        m.put("unpaidCount", receivableMapper.selectCount(Wrappers.<Receivable>lambdaQuery()
                .eq(Receivable::getStatus, "UNPAID")));
        return m;
    }

    public List<Settlement> settlements() {
        return settlementMapper.selectList(Wrappers.<Settlement>lambdaQuery()
                .orderByDesc(Settlement::getId));
    }

    public List<SettlementItem> settlementItems(Long settlementId) {
        return itemMapper.selectList(Wrappers.<SettlementItem>lambdaQuery()
                .eq(SettlementItem::getSettlementId, settlementId));
    }

    public List<Receivable> receivables() {
        return receivableMapper.selectList(Wrappers.<Receivable>lambdaQuery()
                .orderByDesc(Receivable::getId));
    }

    public BigDecimal outstanding() {
        return receivableMapper.sumOutstanding();
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
