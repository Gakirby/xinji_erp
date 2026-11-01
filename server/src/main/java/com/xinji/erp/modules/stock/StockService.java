package com.xinji.erp.modules.stock;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinji.erp.common.AuthContext;
import com.xinji.erp.common.BizException;
import com.xinji.erp.common.IdGen;
import com.xinji.erp.common.UserPrincipal;
import com.xinji.erp.common.cache.MultiLevelCache;
import com.xinji.erp.modules.product.entity.Sku;
import com.xinji.erp.modules.product.mapper.SkuMapper;
import com.xinji.erp.modules.seckill.entity.SeckillSku;
import com.xinji.erp.modules.seckill.mapper.SeckillSkuMapper;
import com.xinji.erp.modules.stock.dto.StockOpRequest;
import com.xinji.erp.modules.stock.entity.StockFlow;
import com.xinji.erp.modules.stock.mapper.StockFlowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存服务：库存双池的唯一入口。
 *
 * 双池模型：
 *   销售可售池 stock_qty      —— 客户下单扣这个
 *   活动库存池 activity_alloc —— 从销售池划拨而来，只有活动订单能扣
 *   冻结 frozen_qty           —— 下单锁定，发货核销
 *
 * 任何一次变动都写一条 t_stock_flow，保证台账能和物理库存对上。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final SkuMapper skuMapper;
    private final StockFlowMapper stockFlowMapper;
    private final SeckillSkuMapper seckillSkuMapper;
    private final MultiLevelCache cache;

    public IPage<StockFlow> pageFlow(Long skuId, String bizType, long page, long size) {
        return stockFlowMapper.selectPage(new Page<>(page, size),
                Wrappers.<StockFlow>lambdaQuery()
                        .eq(skuId != null, StockFlow::getSkuId, skuId)
                        .eq(bizType != null && !bizType.isEmpty(), StockFlow::getBizType, bizType)
                        .orderByDesc(StockFlow::getId));
    }

    /** 划拨：销售可售池 → 活动库存池 */
    @Transactional(rollbackFor = Exception.class)
    public void allocToActivity(StockOpRequest req) {
        Sku sku = requireSku(req.getSkuId());
        BigDecimal before = nz(sku.getStockQty());
        int n = skuMapper.allocToActivity(sku.getId(), req.getQty());
        if (n == 0) {
            throw new BizException("可售库存不足，当前可售 " + before + " 吨，无法划拨 " + req.getQty() + " 吨");
        }
        if (req.getSeckillSkuId() != null) {
            SeckillSku ss = seckillSkuMapper.selectById(req.getSeckillSkuId());
            if (ss == null) {
                throw BizException.notFound("活动 SKU 不存在");
            }
            ss.setActivityStock(nz(ss.getActivityStock()).add(req.getQty()));
            if ("SOLDOUT".equals(ss.getStatus())) {
                ss.setStatus("ON");
            }
            seckillSkuMapper.updateById(ss);
        }
        recordFlow(sku, "ALLOC", req.getQty().negate(), before, before.subtract(req.getQty()),
                req.getBizNo(), req.getRemark() == null ? "划拨活动库存池" : req.getRemark());
        evictSkuAfterCommit(sku.getId());
        log.info("划拨活动库存: sku={} qty={} 可售 {} -> {}", sku.getSkuName(), req.getQty(), before, before.subtract(req.getQty()));
    }

    /** 回拨：活动库存池 → 销售可售池（活动结束剩余量回收） */
    @Transactional(rollbackFor = Exception.class)
    public void returnFromActivity(StockOpRequest req) {
        Sku sku = requireSku(req.getSkuId());
        BigDecimal before = nz(sku.getStockQty());
        int n = skuMapper.returnFromActivity(sku.getId(), req.getQty());
        if (n == 0) {
            throw new BizException("活动池剩余量不足，无法回拨");
        }
        if (req.getSeckillSkuId() != null) {
            SeckillSku ss = seckillSkuMapper.selectById(req.getSeckillSkuId());
            if (ss != null) {
                BigDecimal remain = nz(ss.getActivityStock()).subtract(nz(ss.getSoldQty()));
                BigDecimal back = req.getQty().min(remain.max(BigDecimal.ZERO));
                ss.setActivityStock(nz(ss.getActivityStock()).subtract(back));
                seckillSkuMapper.updateById(ss);
            }
        }
        recordFlow(sku, "RETURN", req.getQty(), before, before.add(req.getQty()),
                req.getBizNo(), req.getRemark() == null ? "活动剩余量回拨销售池" : req.getRemark());
        evictSkuAfterCommit(sku.getId());
    }

    /** 采购入库 */
    @Transactional(rollbackFor = Exception.class)
    public void inbound(StockOpRequest req) {
        Sku sku = requireSku(req.getSkuId());
        BigDecimal before = nz(sku.getStockQty());
        skuMapper.inbound(sku.getId(), req.getQty());
        recordFlow(sku, "INBOUND", req.getQty(), before, before.add(req.getQty()),
                req.getBizNo(), req.getRemark() == null ? "采购到货入库" : req.getRemark());
        evictSkuAfterCommit(sku.getId());
    }

    // ============ 供订单模块调用 ============

    /** 下单冻结：条件更新，返回 false 表示库存不足 */
    public boolean freeze(Long skuId, BigDecimal qty) {
        return skuMapper.freeze(skuId, qty) > 0;
    }

    public void unfreeze(Long skuId, BigDecimal qty) {
        skuMapper.unfreeze(skuId, qty);
    }

    public void consumeFrozen(Long skuId, BigDecimal qty) {
        skuMapper.consumeFrozen(skuId, qty);
    }

    public Sku requireSku(Long skuId) {
        Sku sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw BizException.notFound("物料不存在: " + skuId);
        }
        return sku;
    }

    public void recordFlow(Sku sku, String bizType, BigDecimal qty, BigDecimal before, BigDecimal after,
                           String bizNo, String remark) {
        StockFlow f = new StockFlow();
        f.setFlowNo(IdGen.flowNo());
        f.setSkuId(sku.getId());
        f.setWarehouseId(sku.getWarehouseId());
        f.setBizType(bizType);
        f.setQty(qty);
        f.setBeforeQty(before);
        f.setAfterQty(after);
        f.setBizNo(bizNo);
        f.setRemark(remark);
        UserPrincipal p = AuthContext.get();
        f.setOperator(p == null ? "系统" : p.getDisplayName());
        stockFlowMapper.insert(f);
    }

    public void evictSku(Long skuId) {
        cache.evict("xj:sku:list");
        cache.evict("xj:sku:detail:" + skuId);
    }

    /**
     * 事务提交后再失效缓存。
     * 如果在事务里直接清缓存，并发请求会把还没提交的旧库存读出来重新回填，
     * 结果就是「数据库改了、页面还是旧值」。
     */
    public void evictSkuAfterCommit(Long skuId) {
        if (skuId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictSku(skuId);
                }
            });
        } else {
            evictSku(skuId);
        }
    }

    public List<java.util.Map<String, Object>> summaryByBizType() {
        return stockFlowMapper.sumByBizType();
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
