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
 * 采购单（MVP 只做到货入库单向）
 */
@Data
@TableName("t_purchase_order")
public class PurchaseOrder implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String poNo;
    private String supplierName;
    private Long skuId;
    private String skuName;
    private BigDecimal planQty;
    private BigDecimal arrivedQty;
    private BigDecimal price;
    private BigDecimal arrivedAmount;
    /** PENDING/PARTIAL/DONE */
    private String status;
    private LocalDate expectedDate;
    private LocalDateTime createdAt;
}
