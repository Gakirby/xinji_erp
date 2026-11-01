package com.xinji.erp.common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单据号生成器：前缀 + 日期 + 当日自增序列。
 * 演示项目用进程内自增；生产建议换成 Redis INCR 或号段服务。
 */
public final class IdGen {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ConcurrentHashMap<String, AtomicInteger> SEQ = new ConcurrentHashMap<>();
    private static volatile String currentDay = "";

    private IdGen() {
    }

    public static String next(String prefix) {
        String day = LocalDate.now().format(DAY);
        if (!day.equals(currentDay)) {
            synchronized (IdGen.class) {
                if (!day.equals(currentDay)) {
                    currentDay = day;
                    SEQ.clear();
                }
            }
        }
        AtomicInteger seq = SEQ.computeIfAbsent(prefix, k -> new AtomicInteger(0));
        int n = seq.incrementAndGet();
        // 跨实例部署时用 3 位随机数降低碰撞概率，数据库唯一索引兜底
        int salt = ThreadLocalRandom.current().nextInt(100, 999);
        return prefix + day + String.format("%04d", n) + salt;
    }

    public static String orderNo() {
        return next("XJ");
    }

    public static String flowNo() {
        return next("LS");
    }

    public static String ticketNo() {
        return next("BD");
    }

    public static String tokenNo() {
        return next("TK");
    }

    public static String settlementNo() {
        return next("DZ");
    }

    public static String receivableNo() {
        return next("YS");
    }

    public static String purchaseNo() {
        return next("CG");
    }

    public static String settlePayNo() {
        return next("SK");
    }

    public static String dispatchNo() {
        return next("PC");
    }

    public static String productionNo() {
        return next("SC");
    }

    public static String invoiceNo() {
        return next("FP");
    }

    public static String customerNo() {
        return next("KH");
    }
}
