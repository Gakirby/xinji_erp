package com.xinji.erp.modules.seckill.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 活动 SKU 配置 */
@Data
public class SeckillSkuRequest {

    @NotNull(message = "请选择物料")
    private Long skuId;

    /** 活动价，必须低于挂牌价 */
    @NotNull(message = "请填写活动价")
    @DecimalMin(value = "0.01", message = "活动价必须大于 0")
    private BigDecimal seckillPrice;

    /** 本次从销售可售池划拨的活动库存 */
    @DecimalMin(value = "0", message = "活动库存不能为负数")
    private BigDecimal activityStock = BigDecimal.ZERO;

    /** 每人限购吨数 */
    @DecimalMin(value = "0.001", message = "限购量必须大于 0")
    private BigDecimal limitPerUser;

    /** ON / OFF */
    private String status;
}
