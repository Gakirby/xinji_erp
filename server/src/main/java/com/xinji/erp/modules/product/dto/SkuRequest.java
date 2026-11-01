package com.xinji.erp.modules.product.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 物料新增 / 修改 */
@Data
public class SkuRequest {

    /** 不填则自动生成 */
    private String skuNo;

    @NotBlank(message = "请填写物料名称")
    private String skuName;

    @NotBlank(message = "请选择品种")
    private String category;

    private String spec;

    private String unit = "吨";

    @NotNull(message = "请填写挂牌价")
    @DecimalMin(value = "0", message = "挂牌价不能为负数")
    private BigDecimal price;

    private Long warehouseId;

    private BigDecimal safetyQty;

    private String imageTag;

    /** 建档时的期初库存，会写一条 INBOUND 流水 */
    private BigDecimal initQty;
}
