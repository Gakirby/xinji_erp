package com.xinji.erp.modules.finance;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xinji.erp.common.AuthContext;
import com.xinji.erp.common.BizException;
import com.xinji.erp.common.IdGen;
import com.xinji.erp.common.OrderStatus;
import com.xinji.erp.common.UserPrincipal;
import com.xinji.erp.modules.finance.dto.InvoiceRequest;
import com.xinji.erp.modules.finance.entity.Invoice;
import com.xinji.erp.modules.finance.entity.Receivable;
import com.xinji.erp.modules.finance.entity.Settlement;
import com.xinji.erp.modules.finance.entity.SettlementItem;
import com.xinji.erp.modules.finance.mapper.InvoiceMapper;
import com.xinji.erp.modules.finance.mapper.ReceivableMapper;
import com.xinji.erp.modules.finance.mapper.SettlementItemMapper;
import com.xinji.erp.modules.finance.mapper.SettlementMapper;
import com.xinji.erp.modules.order.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 发票服务。
 *
 * 开票要么挂应收单、要么挂对账批次，不允许手工填金额另开一张 ——
 * 否则发票金额和结算金额永远对不上。
 * 开具成功后，把该批次下的订单从「已对账」推进到「已开票」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.13");

    private final InvoiceMapper invoiceMapper;
    private final ReceivableMapper receivableMapper;
    private final SettlementMapper settlementMapper;
    private final SettlementItemMapper settlementItemMapper;
    private final OrderService orderService;

    public List<Invoice> list(String status, Long customerId) {
        return invoiceMapper.selectList(Wrappers.<Invoice>lambdaQuery()
                .eq(status != null && !status.isEmpty(), Invoice::getStatus, status)
                .eq(customerId != null, Invoice::getCustomerId, customerId)
                .orderByDesc(Invoice::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Invoice create(InvoiceRequest req) {
        if (req.getReceivableId() == null && req.getSettlementId() == null) {
            throw new BizException("请选择要开票的应收单或对账批次");
        }
        Long customerId;
        String customerName;
        BigDecimal amount;

        if (req.getReceivableId() != null) {
            Receivable r = receivableMapper.selectById(req.getReceivableId());
            if (r == null) {
                throw BizException.notFound("应收单不存在");
            }
            Long dup = invoiceMapper.selectCount(Wrappers.<Invoice>lambdaQuery()
                    .eq(Invoice::getReceivableId, r.getId())
                    .ne(Invoice::getStatus, Invoice.VOID));
            if (dup > 0) {
                throw new BizException("应收单 " + r.getReceivableNo() + " 已开过票，不能重复开票");
            }
            customerId = r.getCustomerId();
            customerName = r.getCustomerName();
            amount = req.getAmount() == null ? r.getAmount() : req.getAmount();
            if (amount.compareTo(nz(r.getAmount())) > 0) {
                throw new BizException("开票金额 " + amount + " 元超过应收金额 " + r.getAmount() + " 元");
            }
        } else {
            Settlement s = settlementMapper.selectById(req.getSettlementId());
            if (s == null) {
                throw BizException.notFound("对账批次不存在");
            }
            if (!"CONFIRMED".equals(s.getStatus())) {
                throw new BizException("对账批次 " + s.getSettlementNo() + " 尚未确认，不能开票");
            }
            Long dup = invoiceMapper.selectCount(Wrappers.<Invoice>lambdaQuery()
                    .eq(Invoice::getSettlementId, s.getId())
                    .ne(Invoice::getStatus, Invoice.VOID));
            if (dup > 0) {
                throw new BizException("对账批次 " + s.getSettlementNo() + " 已开过票，不能重复开票");
            }
            customerId = s.getCustomerId();
            customerName = s.getCustomerName();
            amount = req.getAmount() == null ? s.getTotalAmount() : req.getAmount();
            if (amount.compareTo(nz(s.getTotalAmount())) > 0) {
                throw new BizException("开票金额 " + amount + " 元超过对账金额 " + s.getTotalAmount() + " 元");
            }
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("开票金额必须大于 0");
        }

        BigDecimal rate = req.getTaxRate() == null ? DEFAULT_TAX_RATE : req.getTaxRate();
        Invoice inv = new Invoice();
        inv.setInvoiceNo(IdGen.invoiceNo());
        inv.setSettlementId(req.getSettlementId());
        inv.setReceivableId(req.getReceivableId());
        inv.setCustomerId(customerId);
        inv.setCustomerName(customerName);
        inv.setInvoiceType(req.getInvoiceType() == null ? "VAT_SPECIAL" : req.getInvoiceType());
        inv.setAmount(amount);
        inv.setTaxRate(rate);
        // 价税合计 → 税额 = 金额 /(1+税率) × 税率
        inv.setTaxAmount(amount.divide(BigDecimal.ONE.add(rate), 2, RoundingMode.HALF_UP)
                .multiply(rate).setScale(2, RoundingMode.HALF_UP));
        inv.setStatus(Invoice.PENDING);
        inv.setRemark(req.getRemark());
        invoiceMapper.insert(inv);
        log.info("开票申请: {} 客户={} 金额={} 税率={}", inv.getInvoiceNo(), customerName, amount, rate);
        return inv;
    }

    /** 开具：状态推进到已开具，同时把对账批次下订单推进到「已开票」 */
    @Transactional(rollbackFor = Exception.class)
    public Invoice issue(Long id) {
        Invoice inv = require(id);
        if (Invoice.ISSUED.equals(inv.getStatus())) {
            throw new BizException("发票 " + inv.getInvoiceNo() + " 已开具");
        }
        if (Invoice.VOID.equals(inv.getStatus())) {
            throw new BizException("发票已作废，不能开具");
        }
        inv.setStatus(Invoice.ISSUED);
        inv.setIssuedAt(LocalDateTime.now());
        invoiceMapper.updateById(inv);

        if (inv.getSettlementId() != null) {
            List<SettlementItem> items = settlementItemMapper.selectList(
                    Wrappers.<SettlementItem>lambdaQuery()
                            .eq(SettlementItem::getSettlementId, inv.getSettlementId()));
            for (SettlementItem it : items) {
                try {
                    orderService.transfer(it.getOrderNo(), OrderStatus.INVOICED);
                } catch (Exception e) {
                    // 订单可能已取消或已回款，开票不应该因为它整体失败
                    log.warn("订单 {} 未能推进到已开票: {}", it.getOrderNo(), e.getMessage());
                }
            }
        }
        log.info("发票开具完成: {} 金额={}", inv.getInvoiceNo(), inv.getAmount());
        return inv;
    }

    @Transactional(rollbackFor = Exception.class)
    public Invoice voidInvoice(Long id, String reason) {
        Invoice inv = require(id);
        if (Invoice.VOID.equals(inv.getStatus())) {
            throw new BizException("发票已作废");
        }
        inv.setStatus(Invoice.VOID);
        inv.setRemark(reason == null ? inv.getRemark() : reason);
        invoiceMapper.updateById(inv);
        return inv;
    }

    /** 开票统计：已开票金额 / 待开票金额 */
    public Map<String, Object> stats() {
        Map<String, Object> m = new HashMap<>();
        m.put("issuedAmount", invoiceMapper.sumIssued());
        m.put("pendingCount", invoiceMapper.selectCount(Wrappers.<Invoice>lambdaQuery()
                .eq(Invoice::getStatus, Invoice.PENDING)));
        m.put("voidCount", invoiceMapper.selectCount(Wrappers.<Invoice>lambdaQuery()
                .eq(Invoice::getStatus, Invoice.VOID)));
        return m;
    }

    private Invoice require(Long id) {
        Invoice inv = invoiceMapper.selectById(id);
        if (inv == null) {
            throw BizException.notFound("发票不存在: " + id);
        }
        return inv;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 当前操作人，写入发票备注审计用 */
    public String operator() {
        UserPrincipal p = AuthContext.get();
        return p == null ? "系统" : p.getDisplayName();
    }
}
