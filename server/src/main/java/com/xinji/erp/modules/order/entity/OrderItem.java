package com.xinji.erp.modules.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.io.Serializable;

/**
 * 订单明细
 */
@Data
@TableName("t_order_item")
public class OrderItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String orderNo;
    private Long skuId;
    private String skuName;
    private String spec;
    private BigDecimal qty;
    private BigDecimal price;
    private BigDecimal amount;
    /** 结算吨数：按磅单净重扣水扣杂后回填 */
    private BigDecimal settleQty;
    private BigDecimal settleAmount;
}
