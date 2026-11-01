package com.xinji.erp.common;

import java.util.Arrays;
import java.util.List;

/**
 * 订单状态机：提交 → 审核锁库存 → 派车 → 出厂过磅 → 签收 → 对账 → 开票 → 回款
 * 任何环节的单据号都挂在同一个 orderNo 上，财务不需要再拿 Excel 二次对账。
 */
public enum OrderStatus {

    PENDING_AUDIT("待审核"),
    AUDITED("已审核"),
    DISPATCHED("已派车"),
    SHIPPED("已出厂"),
    SIGNED("已签收"),
    SETTLED("已对账"),
    INVOICED("已开票"),
    PAID("已回款"),
    CANCELLED("已取消");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static String labelOf(String code) {
        for (OrderStatus s : values()) {
            if (s.name().equals(code)) {
                return s.label;
            }
        }
        return code;
    }

    /** 是否允许从 from 流转到 to */
    public static boolean canTransfer(String from, String to) {
        if (from == null) {
            return false;
        }
        if (CANCELLED.name().equals(to)) {
            return !PAID.name().equals(from) && !CANCELLED.name().equals(from);
        }
        List<OrderStatus> chain = Arrays.asList(
                PENDING_AUDIT, AUDITED, DISPATCHED, SHIPPED, SIGNED, SETTLED, INVOICED, PAID);
        int a = chain.indexOf(valueOf(from));
        int b = chain.indexOf(valueOf(to));
        return a >= 0 && b == a + 1;
    }
}
