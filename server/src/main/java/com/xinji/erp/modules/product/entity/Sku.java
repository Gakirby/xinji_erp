package com.xinji.erp.modules.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.io.Serializable;

/**
 * 物料SKU。库存三口径：可售 stockQty + 活动划拨 activityAllocQty + 冻结 frozenQty = 物理总库存
 */
@Data
@TableName("t_sku")
public class Sku implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String skuNo;
    private String skuName;
    private String category;
    private String spec;
    private String unit;
    private BigDecimal price;
    private Long warehouseId;
    /** 销售可售池：客户下单只扣这个字段 */
    private BigDecimal stockQty;
    /** 活动池划拨量：划拨后从 stockQty 减出，单独记账 */
    private BigDecimal activityAllocQty;
    /** 订单冻结量：审核通过后冻结，发货时核销 */
    private BigDecimal frozenQty;
    private BigDecimal safetyQty;
    private String imageTag;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
