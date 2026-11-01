package com.xinji.erp.modules.weigh.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class WeighTicketRequest {

    @NotBlank(message = "请填写订单号")
    private String orderNo;

    @NotNull(message = "请选择物料")
    private Long skuId;

    @NotBlank(message = "请填写车牌号")
    private String plateNo;

    private String driverName;
    private String driverPhone;

    @NotNull(message = "请填写毛重")
    @DecimalMin(value = "0.001", message = "毛重必须大于 0")
    private BigDecimal grossWeight;

    @NotNull(message = "请填写皮重")
    @DecimalMin(value = "0.001", message = "皮重必须大于 0")
    private BigDecimal tareWeight;

    /** 含水率，例如 0.012 表示 1.2% */
    private BigDecimal waterRate = BigDecimal.ZERO;

    /** 扣杂量（吨） */
    private BigDecimal impurityDeductWeight = BigDecimal.ZERO;

    private String weighman;
}
