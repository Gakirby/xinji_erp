package com.xinji.erp.modules.finance.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 采购建单请求 */
@Data
public class PurchaseCreateRequest {

    @NotBlank(message = "请填写供应商")
    private String supplierName;

    @NotNull(message = "请选择物料")
    private Long skuId;

    @NotNull(message = "请填写计划采购量")
    @DecimalMin(value = "0.001", message = "计划采购量必须大于 0")
    private BigDecimal planQty;

    @NotNull(message = "请填写采购单价")
    @DecimalMin(value = "0.01", message = "采购单价必须大于 0")
    private BigDecimal price;

    /** 预计到货日期 */
    private LocalDate expectedDate;
}
