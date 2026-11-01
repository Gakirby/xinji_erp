package com.xinji.erp.modules.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 收款流水：应收单的每一笔回款都单独留痕 */
@Data
@TableName("t_payment")
public class Payment implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String paymentNo;
    private Long receivableId;
    private String receivableNo;
    private Long customerId;
    private String customerName;
    private BigDecimal amount;
    /** TRANSFER转账 / CASH现金 / CHECK承兑 / DRAFT汇票 */
    private String payMethod;
    private LocalDateTime payTime;
    private String operator;
    private String remark;
    private LocalDateTime createdAt;
}
