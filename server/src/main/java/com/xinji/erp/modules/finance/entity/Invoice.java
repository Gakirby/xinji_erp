package com.xinji.erp.modules.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 发票：从对账批次或应收单开具，价税分离 */
@Data
@TableName("t_invoice")
public class Invoice implements Serializable {

    public static final String PENDING = "PENDING";
    public static final String ISSUED = "ISSUED";
    public static final String VOID = "VOID";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String invoiceNo;
    private Long settlementId;
    private Long receivableId;
    private Long customerId;
    private String customerName;
    /** VAT_SPECIAL 专票 / VAT_NORMAL 普票 */
    private String invoiceType;
    /** 价税合计 */
    private BigDecimal amount;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private String status;
    private LocalDateTime issuedAt;
    private String remark;
    private LocalDateTime createdAt;
}
