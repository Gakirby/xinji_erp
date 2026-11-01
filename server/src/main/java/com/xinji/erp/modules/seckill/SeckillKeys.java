package com.xinji.erp.modules.seckill;

/**
 * 秒杀相关的 Redis key 约定，集中在此避免各写各的。
 */
public final class SeckillKeys {

    public static final String STOCK = "sk:stock:";
    public static final String USERS = "sk:users:";
    public static final String WARMUP_FLAG = "sk:warmup:";
    public static final String LOCK_TOKEN = "lock:seckill:token:";

    private SeckillKeys() {
    }

    public static String stock(Long seckillSkuId) {
        return STOCK + seckillSkuId;
    }

    public static String users(Long seckillSkuId) {
        return USERS + seckillSkuId;
    }

    public static String warmupFlag(Long activityId) {
        return WARMUP_FLAG + activityId;
    }

    public static String tokenLock(String tokenNo) {
        return LOCK_TOKEN + tokenNo;
    }
}
