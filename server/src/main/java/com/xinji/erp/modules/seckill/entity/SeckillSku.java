package com.xinji.erp.modules.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.io.Serializable;

/**
 * 活动SKU：活动库存独立于销售库存
 */
@Data
@TableName("t_seckill_sku")
public class SeckillSku implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private String seckillSkuNo;
    private Long skuId;
    private String skuName;
    private BigDecimal seckillPrice;
    private BigDecimal originalPrice;
    /** 活动库存：从销售可售池划拨而来的独立池 */
    private BigDecimal activityStock;
    private BigDecimal soldQty;
    private BigDecimal limitPerUser;
    /** ON/OFF/SOLDOUT */
    private String status;
    private LocalDateTime createdAt;
}
