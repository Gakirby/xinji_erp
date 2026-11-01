package com.xinji.erp.modules.order.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class OrderItemRequest {

    @NotNull(message = "请选择物料")
    private Long skuId;

    @NotNull(message = "请输入吨数")
    @DecimalMin(value = "0.001", message = "吨数必须大于 0")
    private BigDecimal qty;
}
