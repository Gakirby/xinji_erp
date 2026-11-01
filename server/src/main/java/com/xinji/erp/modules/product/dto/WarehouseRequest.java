package com.xinji.erp.modules.product.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 料仓新增 */
@Data
public class WarehouseRequest {

    @NotBlank(message = "请填写料仓编号")
    private String warehouseNo;

    @NotBlank(message = "请填写料仓名称")
    private String warehouseName;

    @NotNull(message = "请填写设计容量")
    @DecimalMin(value = "0", message = "设计容量不能为负数")
    private BigDecimal capacity;
}
