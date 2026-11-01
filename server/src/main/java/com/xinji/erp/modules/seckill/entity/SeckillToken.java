package com.xinji.erp.modules.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.io.Serializable;

/**
 * 抢购令牌：抢到的是提货凭证，不是订单。(user_id, seckill_sku_id) 唯一索引兜底一人一单
 */
@Data
@TableName("t_seckill_token")
public class SeckillToken implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tokenNo;
    private Long userId;
    private Long customerId;
    private Long activityId;
    private Long seckillSkuId;
    private Long skuId;
    private BigDecimal qty;
    private BigDecimal price;
    /** PENDING 发放中 / ISSUED 待核销 / USED 已下单 / EXPIRED 已过期 / FAILED 失败 */
    private String status;
    private LocalDateTime expireTime;
    private String orderNo;
    private String failReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
