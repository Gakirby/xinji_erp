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
 * 对账批次
 */
@Data
@TableName("t_settlement")
public class Settlement implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String settlementNo;
    private Long customerId;
    private String customerName;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Integer orderCount;
    private BigDecimal totalQty;
    private BigDecimal totalAmount;
    /** DRAFT/CONFIRMED/INVOICED */
    private String status;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
}
