package com.xinji.erp.modules.product.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品视图。客户侧只看 saleableQty，员工侧看三个口径的拆分明细。
 */
@Data
public class SkuVO {

    private Long id;
    private String skuNo;
    private String skuName;
    private String category;
    private String spec;
    private String unit;
    private BigDecimal price;

    /** 销售可售池（客户下单可用） */
    private BigDecimal saleableQty;
    /** 活动池划拨量 */
    private BigDecimal activityAllocQty;
    /** 订单冻结量 */
    private BigDecimal frozenQty;
    /** 物理总库存 = 可售 + 活动 + 冻结 */
    private BigDecimal totalQty;
    /** 安全库存 */
    private BigDecimal safetyQty;

    private String warehouseNo;
    private String imageTag;
    /** 充足 / 正常 / 偏低 / 紧张 */
    private String stockStatus;
}
