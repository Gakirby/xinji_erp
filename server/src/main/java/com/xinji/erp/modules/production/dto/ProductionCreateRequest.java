package com.xinji.erp.modules.production.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 创建生产工单 */
@Data
public class ProductionCreateRequest {

    @NotBlank(message = "请选择产线")
    private String lineName;

    @NotBlank(message = "请选择班次")
    private String shift;

    @NotNull(message = "请选择产出物料")
    private Long skuId;

    @NotNull(message = "请填写计划产量")
    @DecimalMin(value = "0.001", message = "计划产量必须大于 0")
    private BigDecimal planQty;

    /** 计划投料量，可不填 */
    private BigDecimal feedQty;

    private String operator;
    private String remark;
}
