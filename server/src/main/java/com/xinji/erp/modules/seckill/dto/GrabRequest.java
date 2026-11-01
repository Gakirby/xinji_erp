package com.xinji.erp.modules.seckill.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class GrabRequest {

    @NotNull(message = "请选择活动 SKU")
    private Long seckillSkuId;

    @NotNull(message = "请输入抢购吨数")
    @DecimalMin(value = "0.001", message = "吨数必须大于 0")
    private BigDecimal qty;
}
