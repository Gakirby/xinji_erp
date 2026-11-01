package com.xinji.erp.modules.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.io.Serializable;

/**
 * 订单。所有环节（库存/磅单/对账/应收）都挂在同一个 orderNo 上
 */
@Data
@TableName("t_order")
public class Order implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long customerId;
    private String customerName;
    /** NORMAL 常规订单 / SECKILL 抢购订单 */
    private String orderType;
    /** PENDING_AUDIT/AUDITED/DISPATCHED/SHIPPED/SIGNED/SETTLED/INVOICED/PAID/CANCELLED */
    private String status;
    private BigDecimal totalQty;
    private BigDecimal totalAmount;
    private BigDecimal freight;
    private BigDecimal discountAmount;
    private BigDecimal payAmount;
    private String deliveryType;
    private String expectedDate;
    private String address;
    private String receiver;
    private String seckillToken;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
