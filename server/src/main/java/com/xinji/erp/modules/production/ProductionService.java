package com.xinji.erp.modules.production;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xinji.erp.common.BizException;
import com.xinji.erp.common.IdGen;
import com.xinji.erp.modules.product.entity.Sku;
import com.xinji.erp.modules.production.dto.ProductionCreateRequest;
import com.xinji.erp.modules.production.dto.ProductionFinishRequest;
import com.xinji.erp.modules.production.entity.ProductionOrder;
import com.xinji.erp.modules.production.mapper.ProductionOrderMapper;
import com.xinji.erp.modules.stock.StockService;
import com.xinji.erp.modules.stock.dto.StockOpRequest;
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
 * 生产管理。
 *
 * 完工报产不直接 update t_sku，而是走 StockService.inbound，
 * 由库存服务统一写台账。这样「采购入库 + 生产入库」在台账里能分开看，
 * 物理总库存 = 期初 + 采购入库 + 生产入库 − 出库，随时对得上。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionService {

    private final ProductionOrderMapper productionMapper;
    private final StockService stockService;

    public List<ProductionOrder> list(String status, String keyword) {
        return productionMapper.selectList(Wrappers.<ProductionOrder>lambdaQuery()
                .eq(status != null && !status.isEmpty(), ProductionOrder::getStatus, status)
                .and(keyword != null && !keyword.isEmpty(), w -> w
                        .like(ProductionOrder::getProductionNo, keyword)
                        .or().like(ProductionOrder::getSkuName, keyword)
                        .or().like(ProductionOrder::getLineName, keyword))
                .orderByDesc(ProductionOrder::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductionOrder create(ProductionCreateRequest req) {
        Sku sku = stockService.requireSku(req.getSkuId());
        ProductionOrder o = new ProductionOrder();
        o.setProductionNo(IdGen.productionNo());
        o.setLineName(req.getLineName());
        o.setShift(req.getShift());
        o.setSkuId(sku.getId());
        o.setSkuName(sku.getSkuName());
        o.setPlanQty(req.getPlanQty());
        o.setOutputQty(BigDecimal.ZERO);
        o.setFeedQty(req.getFeedQty() == null ? BigDecimal.ZERO : req.getFeedQty());
        o.setStatus(ProductionOrder.PENDING);
        o.setOperator(req.getOperator());
        o.setRemark(req.getRemark());
        productionMapper.insert(o);
        log.info("生产建单: {} 产线={} 物料={} 计划={}吨", o.getProductionNo(), o.getLineName(),
                o.getSkuName(), o.getPlanQty());
        return o;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductionOrder start(Long id) {
        ProductionOrder o = require(id);
        if (!ProductionOrder.PENDING.equals(o.getStatus())) {
            throw new BizException("工单当前状态为「" + o.getStatus() + "」，只有待开始的工单可以开工");
        }
        o.setStatus(ProductionOrder.RUNNING);
        o.setStartTime(LocalDateTime.now());
        productionMapper.updateById(o);
        return o;
    }

    /**
     * 完工报产：写入实际产量并入销售可售池。
     * 完工后不可重复报产，避免重复入库。
     */
    @Transactional(rollbackFor = Exception.class)
    public ProductionOrder finish(Long id, ProductionFinishRequest req) {
        ProductionOrder o = require(id);
        if (ProductionOrder.DONE.equals(o.getStatus())) {
            throw new BizException("工单 " + o.getProductionNo() + " 已完工，不能重复报产");
        }
        if (ProductionOrder.CANCELLED.equals(o.getStatus())) {
            throw new BizException("工单已取消，不能报产");
        }
        o.setOutputQty(req.getOutputQty());
        if (req.getFeedQty() != null) {
            o.setFeedQty(req.getFeedQty());
        }
        o.setStatus(ProductionOrder.DONE);
        o.setEndTime(LocalDateTime.now());
        if (o.getStartTime() == null) {
            o.setStartTime(o.getEndTime());
        }
        if (req.getRemark() != null) {
            o.setRemark(req.getRemark());
        }
        productionMapper.updateById(o);

        // 生产入库：与采购入库共用库存服务，台账业务单号用生产工单号
        StockOpRequest op = new StockOpRequest();
        op.setSkuId(o.getSkuId());
        op.setQty(req.getOutputQty());
        op.setBizNo(o.getProductionNo());
        op.setRemark("生产完工入库 · " + o.getLineName() + " " + o.getShift());
        stockService.inbound(op);

        log.info("生产完工: {} 产出 {} 吨，已入销售可售池", o.getProductionNo(), req.getOutputQty());
        return o;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductionOrder cancel(Long id, String reason) {
        ProductionOrder o = require(id);
        if (ProductionOrder.DONE.equals(o.getStatus())) {
            throw new BizException("工单已完工入库，不能取消");
        }
        o.setStatus(ProductionOrder.CANCELLED);
        o.setRemark(reason == null ? o.getRemark() : reason);
        o.setEndTime(LocalDateTime.now());
        productionMapper.updateById(o);
        return o;
    }

    /** 生产看板：在产工单数、本月产量、成品率 */
    public Map<String, Object> board() {
        Map<String, Object> m = new HashMap<>();
        m.put("pending", productionMapper.selectCount(Wrappers.<ProductionOrder>lambdaQuery()
                .eq(ProductionOrder::getStatus, ProductionOrder.PENDING)));
        m.put("running", productionMapper.selectCount(Wrappers.<ProductionOrder>lambdaQuery()
                .eq(ProductionOrder::getStatus, ProductionOrder.RUNNING)));
        List<ProductionOrder> done = productionMapper.selectList(Wrappers.<ProductionOrder>lambdaQuery()
                .eq(ProductionOrder::getStatus, ProductionOrder.DONE));
        BigDecimal output = BigDecimal.ZERO;
        BigDecimal feed = BigDecimal.ZERO;
        for (ProductionOrder o : done) {
            output = output.add(nz(o.getOutputQty()));
            feed = feed.add(nz(o.getFeedQty()));
        }
        m.put("totalOutput", output);
        m.put("totalFeed", feed);
        m.put("yieldRate", feed.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : output.divide(feed, 4, RoundingMode.HALF_UP));
        m.put("monthBySku", productionMapper.monthOutputBySku());
        return m;
    }

    private ProductionOrder require(Long id) {
        ProductionOrder o = productionMapper.selectById(id);
        if (o == null) {
            throw BizException.notFound("生产工单不存在: " + id);
        }
        return o;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
