package com.xinji.erp.modules.production.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 完工报产 */
@Data
public class ProductionFinishRequest {

    @NotNull(message = "请填写实际产量")
    @DecimalMin(value = "0.001", message = "实际产量必须大于 0")
    private BigDecimal outputQty;

    /** 实际投料量 */
    private BigDecimal feedQty;

    private String remark;
}
