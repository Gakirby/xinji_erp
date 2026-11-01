package com.xinji.erp.modules.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.io.Serializable;

/**
 * 对账明细
 */
@Data
@TableName("t_settlement_item")
public class SettlementItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long settlementId;
    private Long orderId;
    private String orderNo;
    private BigDecimal settleQty;
    private BigDecimal amount;
    private LocalDate settleDate;
}
