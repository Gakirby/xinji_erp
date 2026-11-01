package com.xinji.erp.modules.weigh.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.io.Serializable;

/**
 * 地磅单：毛重、皮重、净重、扣水、扣杂、结算吨数
 */
@Data
@TableName("t_weigh_ticket")
public class WeighTicket implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String ticketNo;
    private String orderNo;
    private Long skuId;
    private String plateNo;
    private String driverName;
    private String driverPhone;
    private BigDecimal grossWeight;
    private BigDecimal tareWeight;
    private BigDecimal netWeight;
    private BigDecimal waterRate;
    private BigDecimal waterDeductWeight;
    private BigDecimal impurityDeductWeight;
    private BigDecimal settleWeight;
    private LocalDateTime weighTime;
    private String weighman;
    private String status;
    private LocalDateTime createdAt;
}
