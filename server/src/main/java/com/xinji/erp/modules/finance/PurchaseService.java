package com.xinji.erp.modules.finance;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xinji.erp.common.BizException;
import com.xinji.erp.common.IdGen;
import com.xinji.erp.modules.finance.dto.PurchaseArriveRequest;
import com.xinji.erp.modules.finance.dto.PurchaseCreateRequest;
import com.xinji.erp.modules.finance.entity.PurchaseOrder;
import com.xinji.erp.modules.finance.mapper.PurchaseOrderMapper;
import com.xinji.erp.modules.product.entity.Sku;
import com.xinji.erp.modules.stock.StockService;
import com.xinji.erp.modules.stock.dto.StockOpRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购服务（MVP 做到货入库单向）。
 *
 * 采购到货不直接改库存表，而是调用 StockService.inbound，
 * 由库存服务统一写 t_stock_flow 台账，保证「物理库存 = 台账累计」始终成立。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseOrderMapper purchaseOrderMapper;
    private final StockService stockService;

    public List<PurchaseOrder> list(String status) {
        return purchaseOrderMapper.selectList(Wrappers.<PurchaseOrder>lambdaQuery()
                .eq(status != null && !status.isEmpty(), PurchaseOrder::getStatus, status)
                .orderByDesc(PurchaseOrder::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder create(PurchaseCreateRequest req) {
        Sku sku = stockService.requireSku(req.getSkuId());
        PurchaseOrder po = new PurchaseOrder();
        po.setPoNo(IdGen.purchaseNo());
        po.setSupplierName(req.getSupplierName());
        po.setSkuId(sku.getId());
        po.setSkuName(sku.getSkuName());
        po.setPlanQty(req.getPlanQty());
        po.setArrivedQty(BigDecimal.ZERO);
        po.setPrice(req.getPrice());
        po.setArrivedAmount(BigDecimal.ZERO);
        po.setStatus("PENDING");
        po.setExpectedDate(req.getExpectedDate());
        purchaseOrderMapper.insert(po);
        log.info("采购建单: {} 供应商={} 物料={} 计划={}吨", po.getPoNo(), po.getSupplierName(), po.getSkuName(), po.getPlanQty());
        return po;
    }

    /**
     * 到货登记：累加到货量、算到货金额、刷新单据状态，并入库到销售可售池。
     */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder arrive(Long id, PurchaseArriveRequest req) {
        PurchaseOrder po = purchaseOrderMapper.selectById(id);
        if (po == null) {
            throw BizException.notFound("采购单不存在: " + id);
        }
        if ("DONE".equals(po.getStatus())) {
            throw new BizException("采购单 " + po.getPoNo() + " 已全部到货，不能重复登记");
        }
        BigDecimal plan = nz(po.getPlanQty());
        BigDecimal arrived = nz(po.getArrivedQty()).add(req.getArrivedQty());
        if (arrived.compareTo(plan) > 0) {
            throw new BizException("到货量超出计划量：计划 " + plan + " 吨，已到 " + nz(po.getArrivedQty())
                    + " 吨，本次最多可登记 " + plan.subtract(nz(po.getArrivedQty())) + " 吨");
        }

        po.setArrivedQty(arrived);
        po.setArrivedAmount(arrived.multiply(nz(po.getPrice())));
        po.setStatus(arrived.compareTo(plan) >= 0 ? "DONE" : "PARTIAL");
        purchaseOrderMapper.updateById(po);

        // 入库到销售可售池，台账业务单号用采购单号
        StockOpRequest op = new StockOpRequest();
        op.setSkuId(po.getSkuId());
        op.setQty(req.getArrivedQty());
        op.setBizNo(po.getPoNo());
        op.setRemark(req.getRemark() == null ? "采购到货入库 · " + po.getSupplierName() : req.getRemark());
        stockService.inbound(op);

        log.info("采购到货: {} 本次={}吨 累计={}/{}吨 状态={}",
                po.getPoNo(), req.getArrivedQty(), arrived, plan, po.getStatus());
        return po;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public PurchaseOrder detail(Long id) {
        PurchaseOrder po = purchaseOrderMapper.selectById(id);
        if (po == null) {
            throw BizException.notFound("采购单不存在: " + id);
        }
        return po;
    }

    /**
     * 取消采购单。
     * 只允许「一车未到」的单子取消 —— 已经入库的量已经进了物理库存并写了台账，
     * 直接改单据状态会让台账和库存对不上，需要走退货流程而不是取消。
     */
    public PurchaseOrder cancel(Long id, String reason) {
        PurchaseOrder po = detail(id);
        if ("DONE".equals(po.getStatus())) {
            throw new BizException("采购单已全部到货，不能取消");
        }
        if (nz(po.getArrivedQty()).compareTo(BigDecimal.ZERO) > 0) {
            throw new BizException("采购单已有 " + po.getArrivedQty()
                    + " 吨到货入库，不能直接取消，请走退货流程");
        }
        po.setStatus("CANCELLED");
        purchaseOrderMapper.updateById(po);
        log.info("采购单取消: {} 原因={}", po.getPoNo(), reason);
        return po;
    }
}
