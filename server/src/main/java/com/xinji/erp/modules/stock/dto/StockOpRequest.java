package com.xinji.erp.modules.stock.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class StockOpRequest {

    @NotNull(message = "请选择物料")
    private Long skuId;

    @NotNull(message = "请输入数量")
    @DecimalMin(value = "0.001", message = "数量必须大于 0")
    private BigDecimal qty;

    /** 划拨/回拨时必填：目标活动 SKU */
    private Long seckillSkuId;

    private String bizNo;

    private String remark;
}
