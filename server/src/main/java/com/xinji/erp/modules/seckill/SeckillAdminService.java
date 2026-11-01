package com.xinji.erp.modules.seckill;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xinji.erp.common.BizException;
import com.xinji.erp.common.IdGen;
import com.xinji.erp.modules.product.entity.Sku;
import com.xinji.erp.modules.seckill.dto.ActivityRequest;
import com.xinji.erp.modules.seckill.dto.SeckillSkuRequest;
import com.xinji.erp.modules.seckill.entity.SeckillActivity;
import com.xinji.erp.modules.seckill.entity.SeckillSku;
import com.xinji.erp.modules.seckill.mapper.SeckillActivityMapper;
import com.xinji.erp.modules.seckill.mapper.SeckillSkuMapper;
import com.xinji.erp.modules.stock.StockService;
import com.xinji.erp.modules.stock.dto.StockOpRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀活动运营：活动建档、活动 SKU 配置、库存划拨与回收。
 *
 * 关键约束：活动库存只能从销售可售池「划拨」进来，不能凭空写一个数字。
 * 划拨会同时扣 stock_qty、加 activity_alloc_qty 并写台账；
 * 活动结束未售出的量再回拨销售池，物理总库存始终守恒。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillAdminService {

    private final SeckillActivityMapper activityMapper;
    private final SeckillSkuMapper seckillSkuMapper;
    private final StockService stockService;
    private final StringRedisTemplate redis;

    // ==================== 活动 ====================

    @Transactional(rollbackFor = Exception.class)
    public SeckillActivity save(Long id, ActivityRequest req) {
        if (req.getEndTime().isBefore(req.getStartTime())) {
            throw new BizException("结束时间不能早于开始时间");
        }
        SeckillActivity a = id == null ? new SeckillActivity() : requireActivity(id);
        if (id != null && ("RUNNING".equals(a.getStatus()) || "ENDED".equals(a.getStatus()))) {
            throw new BizException("活动已开始或已结束，不能修改时间");
        }
        a.setActivityName(req.getActivityName());
        a.setStartTime(req.getStartTime());
        a.setEndTime(req.getEndTime());
        if (id == null) {
            a.setActivityNo(IdGen.next("ASK"));
            a.setStatus("DRAFT");
            a.setWarmupStatus("NONE");
            activityMapper.insert(a);
        } else {
            activityMapper.updateById(a);
        }
        log.info("秒杀活动保存: {} {}", a.getActivityNo(), a.getActivityName());
        return a;
    }

    /** 发布：DRAFT → PUBLISHED，发布后才允许预热和抢购 */
    public SeckillActivity publish(Long id) {
        SeckillActivity a = requireActivity(id);
        if (!"DRAFT".equals(a.getStatus())) {
            throw new BizException("活动当前状态为「" + a.getStatus() + "」，只有草稿可以发布");
        }
        long skuCount = seckillSkuMapper.selectCount(Wrappers.<SeckillSku>lambdaQuery()
                .eq(SeckillSku::getActivityId, id));
        if (skuCount == 0) {
            throw new BizException("活动下还没有配置任何物料，不能发布");
        }
        a.setStatus("PUBLISHED");
        activityMapper.updateById(a);
        return a;
    }

    public SeckillActivity start(Long id) {
        SeckillActivity a = requireActivity(id);
        if ("ENDED".equals(a.getStatus())) {
            throw new BizException("活动已结束");
        }
        if ("DRAFT".equals(a.getStatus())) {
            throw new BizException("活动还没发布，不能开始");
        }
        a.setStatus("RUNNING");
        activityMapper.updateById(a);
        return a;
    }

    /**
     * 结束活动：未售出的活动库存回拨到销售可售池。
     * 不回拨的话，这部分量会永远卡在活动池里，物理库存看得见、卖不了。
     */
    @Transactional(rollbackFor = Exception.class)
    public SeckillActivity finish(Long id) {
        SeckillActivity a = requireActivity(id);
        if ("ENDED".equals(a.getStatus())) {
            throw new BizException("活动已结束");
        }
        List<SeckillSku> skus = seckillSkuMapper.selectList(Wrappers.<SeckillSku>lambdaQuery()
                .eq(SeckillSku::getActivityId, id));
        BigDecimal returned = BigDecimal.ZERO;
        for (SeckillSku ss : skus) {
            BigDecimal remain = nz(ss.getActivityStock()).subtract(nz(ss.getSoldQty()));
            if (remain.compareTo(BigDecimal.ZERO) > 0) {
                StockOpRequest op = new StockOpRequest();
                op.setSkuId(ss.getSkuId());
                op.setQty(remain);
                op.setSeckillSkuId(ss.getId());
                op.setBizNo(a.getActivityNo());
                op.setRemark("活动结束，未售出活动库存回拨销售池");
                stockService.returnFromActivity(op);
                returned = returned.add(remain);
            }
            ss.setStatus("OFF");
            seckillSkuMapper.updateById(ss);
        }
        a.setStatus("ENDED");
        activityMapper.updateById(a);
        // 活动结束，清掉预热标记，避免下次预热复用到旧的库存 key
        redis.delete(SeckillKeys.warmupFlag(id));
        log.info("秒杀活动结束: {} 回拨销售池 {} 吨", a.getActivityNo(), returned);
        return a;
    }

    // ==================== 活动 SKU ====================

    @Transactional(rollbackFor = Exception.class)
    public SeckillSku addSku(Long activityId, SeckillSkuRequest req) {
        SeckillActivity a = requireActivity(activityId);
        if ("ENDED".equals(a.getStatus())) {
            throw new BizException("活动已结束，不能追加物料");
        }
        Sku sku = stockService.requireSku(req.getSkuId());
        if (req.getSeckillPrice().compareTo(nz(sku.getPrice())) >= 0) {
            throw new BizException("活动价 " + req.getSeckillPrice() + " 元必须低于挂牌价 "
                    + sku.getPrice() + " 元");
        }
        SeckillSku dup = seckillSkuMapper.selectOne(Wrappers.<SeckillSku>lambdaQuery()
                .eq(SeckillSku::getActivityId, activityId)
                .eq(SeckillSku::getSkuId, req.getSkuId())
                .ne(SeckillSku::getStatus, "OFF"));
        if (dup != null) {
            throw new BizException("物料「" + sku.getSkuName() + "」已在本次活动中");
        }

        BigDecimal limit = req.getLimitPerUser() == null ? req.getActivityStock() : req.getLimitPerUser();
        SeckillSku ss = new SeckillSku();
        ss.setActivityId(activityId);
        ss.setSeckillSkuNo(IdGen.next("AS"));
        ss.setSkuId(sku.getId());
        ss.setSkuName(sku.getSkuName());
        ss.setSeckillPrice(req.getSeckillPrice());
        ss.setOriginalPrice(sku.getPrice());
        ss.setActivityStock(BigDecimal.ZERO);
        ss.setSoldQty(BigDecimal.ZERO);
        ss.setLimitPerUser(limit == null ? BigDecimal.ZERO : limit);
        ss.setStatus(req.getStatus() == null ? "ON" : req.getStatus());
        seckillSkuMapper.insert(ss);

        // 划拨活动库存：销售可售池 → 活动池
        if (req.getActivityStock() != null && req.getActivityStock().compareTo(BigDecimal.ZERO) > 0) {
            StockOpRequest op = new StockOpRequest();
            op.setSkuId(sku.getId());
            op.setQty(req.getActivityStock());
            op.setSeckillSkuId(ss.getId());
            op.setBizNo(a.getActivityNo());
            op.setRemark("划拨活动库存池 · " + a.getActivityName());
            stockService.allocToActivity(op);
            ss = seckillSkuMapper.selectById(ss.getId());
        }
        // 活动配置变了，预热状态需要重做
        a.setWarmupStatus("PENDING");
        activityMapper.updateById(a);
        redis.delete(SeckillKeys.warmupFlag(activityId));
        log.info("活动 SKU 新增: 活动={} 物料={} 活动价={} 划拨={}吨", a.getActivityNo(),
                sku.getSkuName(), ss.getSeckillPrice(), nz(ss.getActivityStock()));
        return ss;
    }

    @Transactional(rollbackFor = Exception.class)
    public SeckillSku updateSku(Long seckillSkuId, SeckillSkuRequest req) {
        SeckillSku ss = requireSeckillSku(seckillSkuId);
        if (req.getSeckillPrice() != null) {
            if (req.getSeckillPrice().compareTo(nz(ss.getOriginalPrice())) >= 0) {
                throw new BizException("活动价必须低于原价 " + ss.getOriginalPrice() + " 元");
            }
            ss.setSeckillPrice(req.getSeckillPrice());
        }
        if (req.getLimitPerUser() != null && req.getLimitPerUser().compareTo(BigDecimal.ZERO) > 0) {
            ss.setLimitPerUser(req.getLimitPerUser());
        }
        if (req.getStatus() != null) {
            ss.setStatus(req.getStatus());
        }
        seckillSkuMapper.updateById(ss);
        return ss;
    }

    /** 移除活动 SKU：已抢部分不退，未售部分回拨销售池 */
    @Transactional(rollbackFor = Exception.class)
    public SeckillSku removeSku(Long seckillSkuId, String reason) {
        SeckillSku ss = requireSeckillSku(seckillSkuId);
        BigDecimal remain = nz(ss.getActivityStock()).subtract(nz(ss.getSoldQty()));
        if (remain.compareTo(BigDecimal.ZERO) > 0) {
            StockOpRequest op = new StockOpRequest();
            op.setSkuId(ss.getSkuId());
            op.setQty(remain);
            op.setSeckillSkuId(ss.getId());
            op.setRemark(reason == null ? "移除活动物料，未售库存回拨销售池" : reason);
            stockService.returnFromActivity(op);
        }
        ss.setStatus("OFF");
        seckillSkuMapper.updateById(ss);
        log.info("活动 SKU 移除: {} 回拨 {} 吨", ss.getSkuName(), remain);
        return ss;
    }

    private SeckillActivity requireActivity(Long id) {
        SeckillActivity a = activityMapper.selectById(id);
        if (a == null) {
            throw BizException.notFound("秒杀活动不存在: " + id);
        }
        return a;
    }

    private SeckillSku requireSeckillSku(Long id) {
        SeckillSku ss = seckillSkuMapper.selectById(id);
        if (ss == null) {
            throw BizException.notFound("活动 SKU 不存在: " + id);
        }
        return ss;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 活动状态随时间自动推进：到点开始 / 到点结束（每分钟扫一次） */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 * * * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void autoAdvanceStatus() {
        LocalDateTime now = LocalDateTime.now();
        List<SeckillActivity> list = activityMapper.selectList(Wrappers.<SeckillActivity>lambdaQuery()
                .in(SeckillActivity::getStatus, "PUBLISHED", "RUNNING"));
        for (SeckillActivity a : list) {
            boolean changed = false;
            if ("PUBLISHED".equals(a.getStatus()) && a.getStartTime() != null
                    && !now.isBefore(a.getStartTime()) && now.isBefore(a.getEndTime())) {
                a.setStatus("RUNNING");
                changed = true;
            } else if (a.getEndTime() != null && !now.isBefore(a.getEndTime())) {
                a.setStatus("ENDED");
                changed = true;
            }
            if (changed) {
                activityMapper.updateById(a);
                log.info("活动状态自动推进: {} -> {}", a.getActivityNo(), a.getStatus());
            }
        }
    }
}
