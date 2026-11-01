package com.xinji.erp.modules.seckill.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SeckillSkuVO {

    private Long id;
    private Long activityId;
    private String seckillSkuNo;
    private Long skuId;
    private String skuName;
    private BigDecimal seckillPrice;
    private BigDecimal originalPrice;
    private BigDecimal activityStock;
    private BigDecimal soldQty;
    /** 剩余活动库存 */
    private BigDecimal remainQty;
    private BigDecimal limitPerUser;
    private String status;
    /** 客户端展示用的销售可售量，用于对照双池 */
    private BigDecimal saleableQty;
}
