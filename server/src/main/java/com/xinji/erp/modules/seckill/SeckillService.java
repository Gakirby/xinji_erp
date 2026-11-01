package com.xinji.erp.modules.seckill;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xinji.erp.common.AuthContext;
import com.xinji.erp.common.BizException;
import com.xinji.erp.common.IdGen;
import com.xinji.erp.common.UserPrincipal;
import com.xinji.erp.modules.order.OrderService;
import com.xinji.erp.modules.order.dto.CreateOrderRequest;
import com.xinji.erp.modules.order.vo.OrderVO;
import com.xinji.erp.modules.product.entity.Sku;
import com.xinji.erp.modules.product.mapper.SkuMapper;
import com.xinji.erp.modules.seckill.dto.GrabRequest;
import com.xinji.erp.modules.seckill.dto.GrabResult;
import com.xinji.erp.modules.seckill.entity.SeckillActivity;
import com.xinji.erp.modules.seckill.entity.SeckillSku;
import com.xinji.erp.modules.seckill.entity.SeckillToken;
import com.xinji.erp.modules.seckill.mapper.SeckillActivityMapper;
import com.xinji.erp.modules.seckill.mapper.SeckillSkuMapper;
import com.xinji.erp.modules.seckill.mapper.SeckillTokenMapper;
import com.xinji.erp.modules.seckill.vo.SeckillSkuVO;
import com.xinji.erp.modules.stock.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀主流程：
 *   预热 → 抢购（Lua 原子）→ 异步发放（Stream）→ 令牌核销下单 → 超时释放
 *
 * 关键点：活动库存是独立池，抢购只动活动池，销售可售池不受影响。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    private final SeckillActivityMapper activityMapper;
    private final SeckillSkuMapper seckillSkuMapper;
    private final SeckillTokenMapper tokenMapper;
    private final SkuMapper skuMapper;
    private final OrderService orderService;
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> seckillScript;
    private final StockService stockService;

    @Value("${xinji.seckill.stream-key:sk:stream:token}")
    private String streamKey;

    @Value("${xinji.seckill.token-expire-minutes:15}")
    private int expireMinutes;

    // ==================== 查询 ====================

    public List<SeckillActivity> activities() {
        return activityMapper.selectList(Wrappers.<SeckillActivity>lambdaQuery()
                .orderByDesc(SeckillActivity::getId));
    }

    public List<SeckillSkuVO> activitySkus(Long activityId) {
        List<SeckillSku> list = seckillSkuMapper.selectList(Wrappers.<SeckillSku>lambdaQuery()
                .eq(SeckillSku::getActivityId, activityId)
                .orderByAsc(SeckillSku::getId));
        List<SeckillSkuVO> result = new ArrayList<>(list.size());
        for (SeckillSku s : list) {
            SeckillSkuVO v = new SeckillSkuVO();
            v.setId(s.getId());
            v.setActivityId(s.getActivityId());
            v.setSeckillSkuNo(s.getSeckillSkuNo());
            v.setSkuId(s.getSkuId());
            v.setSkuName(s.getSkuName());
            v.setSeckillPrice(s.getSeckillPrice());
            v.setOriginalPrice(s.getOriginalPrice());
            v.setActivityStock(s.getActivityStock());
            v.setSoldQty(s.getSoldQty());
            v.setRemainQty(nz(s.getActivityStock()).subtract(nz(s.getSoldQty())));
            v.setLimitPerUser(s.getLimitPerUser());
            v.setStatus(s.getStatus());
            Sku sku = skuMapper.selectById(s.getSkuId());
            if (sku != null) {
                v.setSaleableQty(nz(sku.getStockQty()));
            }
            result.add(v);
        }
        return result;
    }

    // ==================== 预热 ====================

    /** 把活动库存写进 Redis，并从数据库恢复「已抢用户集合」，保证重启后一人一单依然有效 */
    @Transactional(rollbackFor = Exception.class)
    public void warmup(Long activityId) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw BizException.notFound("活动不存在");
        }
        List<SeckillSku> list = seckillSkuMapper.selectList(Wrappers.<SeckillSku>lambdaQuery()
                .eq(SeckillSku::getActivityId, activityId));
        if (list.isEmpty()) {
            throw new BizException("该活动还没有配置活动 SKU");
        }
        for (SeckillSku s : list) {
            BigDecimal remain = nz(s.getActivityStock()).subtract(nz(s.getSoldQty()));
            if (remain.compareTo(BigDecimal.ZERO) < 0) {
                remain = BigDecimal.ZERO;
            }
            redis.opsForValue().set(SeckillKeys.stock(s.getId()), remain.stripTrailingZeros().toPlainString());
            redis.delete(SeckillKeys.users(s.getId()));

            List<SeckillToken> issued = tokenMapper.selectList(Wrappers.<SeckillToken>lambdaQuery()
                    .eq(SeckillToken::getSeckillSkuId, s.getId())
                    .in(SeckillToken::getStatus, Arrays.asList("ISSUED", "USED")));
            for (SeckillToken t : issued) {
                redis.opsForSet().add(SeckillKeys.users(s.getId()), String.valueOf(t.getUserId()));
            }
            log.info("预热活动SKU {} 剩余 {} 吨，恢复已抢用户 {} 人", s.getSeckillSkuNo(), remain, issued.size());
        }
        activity.setWarmupStatus("DONE");
        activityMapper.updateById(activity);
        redis.opsForValue().set(SeckillKeys.warmupFlag(activityId), "DONE", 7, TimeUnit.DAYS);
    }

    // ==================== 抢购 ====================

    public GrabResult grab(GrabRequest req) {
        UserPrincipal p = AuthContext.require();
        SeckillSku ss = seckillSkuMapper.selectById(req.getSeckillSkuId());
        if (ss == null) {
            throw BizException.notFound("活动 SKU 不存在");
        }
        SeckillActivity activity = activityMapper.selectById(ss.getActivityId());
        if (activity == null) {
            throw BizException.notFound("活动不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime())) {
            throw new BizException("活动尚未开始");
        }
        if (now.isAfter(activity.getEndTime())) {
            throw new BizException("活动已结束");
        }

        String tokenNo = IdGen.tokenNo();
        List<String> keys = Arrays.asList(
                SeckillKeys.stock(ss.getId()),
                SeckillKeys.users(ss.getId()),
                streamKey);

        Long r = redis.execute(seckillScript, keys,
                String.valueOf(p.getUserId()),
                req.getQty().stripTrailingZeros().toPlainString(),
                tokenNo,
                String.valueOf(ss.getId()),
                String.valueOf(ss.getActivityId()),
                ss.getSeckillPrice().toPlainString(),
                nz(ss.getLimitPerUser()).stripTrailingZeros().toPlainString());

        GrabResult result = new GrabResult();
        result.setSeckillSkuId(ss.getId());
        long code = r == null ? -2 : r;
        if (code == -1) {
            result.setSuccess(false);
            result.setReason("每人限抢一份，你已经抢到过了");
        } else if (code == -2) {
            result.setSuccess(false);
            result.setReason("活动库存尚未预热，请稍后再试");
        } else if (code == -3) {
            result.setSuccess(false);
            result.setReason("活动库存不足，已抢完");
        } else if (code == -4) {
            result.setSuccess(false);
            result.setReason("超过每人限购 " + ss.getLimitPerUser() + " 吨");
        } else {
            result.setSuccess(true);
            result.setTokenNo(tokenNo);
            result.setRemainStock(code);
        }
        log.info("抢购结果: user={} ask={} qty={} -> {}", p.getUserId(), ss.getId(), req.getQty(),
                result.isSuccess() ? "成功 " + tokenNo : result.getReason());
        return result;
    }

    /** 前端轮询发放结果 */
    public SeckillToken queryToken(String tokenNo) {
        SeckillToken token = tokenMapper.selectOne(Wrappers.<SeckillToken>lambdaQuery()
                .eq(SeckillToken::getTokenNo, tokenNo));
        if (token == null) {
            throw BizException.notFound("令牌不存在，可能还在发放中");
        }
        return token;
    }

    public List<SeckillToken> myTokens() {
        Long userId = AuthContext.userId();
        return tokenMapper.selectList(Wrappers.<SeckillToken>lambdaQuery()
                .eq(SeckillToken::getUserId, userId)
                .orderByDesc(SeckillToken::getId));
    }

    // ==================== 核销下单 ====================

    @Transactional(rollbackFor = Exception.class)
    public OrderVO useToken(String tokenNo, CreateOrderRequest req) {
        SeckillToken token = tokenMapper.selectOne(Wrappers.<SeckillToken>lambdaQuery()
                .eq(SeckillToken::getTokenNo, tokenNo));
        if (token == null) {
            throw BizException.notFound("令牌不存在");
        }
        if (!"ISSUED".equals(token.getStatus())) {
            throw new BizException("令牌状态为「" + token.getStatus() + "」，无法下单");
        }
        if (token.getExpireTime() != null && token.getExpireTime().isBefore(LocalDateTime.now())) {
            releaseToken(token);
            throw new BizException("令牌已过期，活动库存已释放");
        }
        if (token.getCustomerId() == null) {
            token.setCustomerId(AuthContext.require().getCustomerId());
        }
        OrderVO order = orderService.createFromToken(token, req);
        int n = tokenMapper.useToken(tokenNo, order.getOrder().getOrderNo());
        if (n == 0) {
            throw new BizException("令牌已被使用");
        }
        // 活动池已扣减，商品列表缓存必须失效，否则客户端还看到核销前的活动划拨量。
        // 注意必须等事务提交后再清，否则并发读会把未提交的旧值回填进缓存。
        evictSkuCache(token.getSkuId());
        return order;
    }

    // ==================== 超时释放 ====================

    /** 每分钟扫描一次过期令牌：作废 + 归还活动库存 + 允许重新抢 */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    public void releaseExpiredTokens() {
        List<SeckillToken> list = tokenMapper.selectList(Wrappers.<SeckillToken>lambdaQuery()
                .eq(SeckillToken::getStatus, "ISSUED")
                .lt(SeckillToken::getExpireTime, LocalDateTime.now())
                .last("limit 100"));
        if (list.isEmpty()) {
            return;
        }
        for (SeckillToken t : list) {
            try {
                releaseToken(t);
            } catch (Exception e) {
                log.error("释放过期令牌失败: {}", t.getTokenNo(), e);
            }
        }
        log.info("过期令牌释放完成，共 {} 条", list.size());
    }

    private void releaseToken(SeckillToken token) {
        int n = tokenMapper.expireToken(token.getTokenNo(), LocalDateTime.now());
        if (n == 0) {
            return;
        }
        SeckillSku ss = seckillSkuMapper.selectById(token.getSeckillSkuId());
        if (ss != null) {
            seckillSkuMapper.restoreStock(token.getSeckillSkuId(), token.getQty());
            skuMapper.returnActivity(ss.getSkuId(), token.getQty());
            if (redis.hasKey(SeckillKeys.stock(ss.getId()))) {
                redis.opsForValue().increment(SeckillKeys.stock(ss.getId()),
                        token.getQty().longValue());
            }
        }
        redis.opsForSet().remove(SeckillKeys.users(token.getSeckillSkuId()),
                String.valueOf(token.getUserId()));
        evictSkuCache(token.getSkuId());
        log.info("令牌过期释放: {} 归还 {} 吨", token.getTokenNo(), token.getQty());
    }

    private void evictSkuCache(Long skuId) {
        stockService.evictSkuAfterCommit(skuId);
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
