package com.xinji.erp.modules.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.io.Serializable;

/**
 * 应收账款
 */
@Data
@TableName("t_receivable")
public class Receivable implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String receivableNo;
    private Long customerId;
    private String customerName;
    private Long settlementId;
    private BigDecimal amount;
    private BigDecimal receivedAmount;
    /** UNPAID/PARTIAL/PAID */
    private String status;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
}
