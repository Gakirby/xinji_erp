package com.xinji.erp.modules.seckill;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xinji.erp.common.BizException;
import com.xinji.erp.modules.product.entity.Sku;
import com.xinji.erp.modules.product.mapper.SkuMapper;
import com.xinji.erp.modules.seckill.entity.SeckillSku;
import com.xinji.erp.modules.seckill.entity.SeckillToken;
import com.xinji.erp.modules.seckill.mapper.SeckillSkuMapper;
import com.xinji.erp.modules.seckill.mapper.SeckillTokenMapper;
import com.xinji.erp.modules.stock.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 令牌发放：真正把「抢到的资格」落成数据库记录。
 *
 * 三重幂等：
 *   1. Redisson 用户维度分布式锁，把同一用户的并发请求串起来；
 *   2. t_seckill_token 的 (user_id, seckill_sku_id) 唯一索引，重复消费插不进去；
 *   3. 活动库存的条件扣减 activity_stock - sold_qty >= qty，重复执行不会多扣。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenIssuer {

    private final SeckillTokenMapper tokenMapper;
    private final SeckillSkuMapper seckillSkuMapper;
    private final SkuMapper skuMapper;
    private final RedissonClient redisson;
    private final StockService stockService;

    @Value("${xinji.seckill.token-expire-minutes:15}")
    private int expireMinutes;

    /**
     * 处理一条发放消息。返回 true 表示处理完成（可 ACK），false 表示需要重试。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean issue(Map<Object, Object> body) {
        String tokenNo = str(body.get("tokenNo"));
        String userIdStr = str(body.get("userId"));
        String skuIdStr = str(body.get("seckillSkuId"));
        String activityIdStr = str(body.get("activityId"));
        String qtyStr = str(body.get("qty"));
        String priceStr = str(body.get("price"));

        if (tokenNo == null || userIdStr == null || skuIdStr == null) {
            log.warn("发放消息字段缺失，丢弃: {}", body);
            return true;
        }

        Long userId = Long.valueOf(userIdStr);
        Long seckillSkuId = Long.valueOf(skuIdStr);
        BigDecimal qty = new BigDecimal(qtyStr);
        BigDecimal price = priceStr == null ? BigDecimal.ZERO : new BigDecimal(priceStr);

        RLock lock = redisson.getLock(SeckillKeys.tokenLock(tokenNo));
        boolean locked = false;
        try {
            locked = lock.tryLock(3, 15, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("获取发放锁超时，稍后由 pending-list 补偿: {}", tokenNo);
                return false;
            }

            // 幂等一：令牌已存在直接跳过
            SeckillToken exist = tokenMapper.selectOne(Wrappers.<SeckillToken>lambdaQuery()
                    .eq(SeckillToken::getTokenNo, tokenNo));
            if (exist != null) {
                log.info("令牌已发放，跳过重复消费: {}", tokenNo);
                return true;
            }

            // 幂等二：数据库条件扣减活动库存
            int deducted = seckillSkuMapper.deductStock(seckillSkuId, qty);
            if (deducted == 0) {
                log.warn("活动库存扣减失败（可能已售罄），令牌置失败: {}", tokenNo);
                saveFailed(userId, seckillSkuId, qty, price, tokenNo, "活动库存不足，发放失败");
                return true;
            }

            SeckillSku ss = seckillSkuMapper.selectById(seckillSkuId);
            SeckillToken token = new SeckillToken();
            token.setTokenNo(tokenNo);
            token.setUserId(userId);
            token.setActivityId(ss == null ? null : ss.getActivityId());
            token.setSeckillSkuId(seckillSkuId);
            token.setSkuId(ss == null ? null : ss.getSkuId());
            token.setQty(qty);
            token.setPrice(price);
            token.setStatus("ISSUED");
            token.setExpireTime(LocalDateTime.now().plusMinutes(expireMinutes));

            // 幂等三：唯一索引兜底，并发下重复插入会抛 DuplicateKeyException
            try {
                tokenMapper.insert(token);
            } catch (DuplicateKeyException e) {
                log.info("唯一索引拦截重复发放: user={} sku={}", userId, seckillSkuId);
                return true;
            }

            // 活动池核销：从 activity_alloc_qty 里扣掉，销售可售池不受影响
            if (ss != null && ss.getSkuId() != null) {
                skuMapper.consumeActivity(ss.getSkuId(), qty);
                // 活动划拨量变了，商品缓存要失效（事务提交后执行）
                stockService.evictSkuAfterCommit(ss.getSkuId());
            }
            log.info("令牌发放成功: {} user={} sku={} qty={}", tokenNo, userId, seckillSkuId, qty);
            return true;
        } catch (Exception e) {
            log.error("令牌发放异常，留给 pending-list 补偿: {}", tokenNo, e);
            return false;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void saveFailed(Long userId, Long seckillSkuId, BigDecimal qty, BigDecimal price,
                            String tokenNo, String reason) {
        SeckillToken token = new SeckillToken();
        token.setTokenNo(tokenNo);
        token.setUserId(userId);
        token.setSeckillSkuId(seckillSkuId);
        token.setQty(qty);
        token.setPrice(price);
        token.setStatus("FAILED");
        token.setFailReason(reason);
        tokenMapper.insert(token);
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
