package com.xinji.erp.modules.production.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产工单：破碎线 / 制砂线加工。
 * 完工入库调 StockService.inbound，和采购入库走同一条台账，不另开一套库存账。
 */
@Data
@TableName("t_production_order")
public class ProductionOrder implements Serializable {

    public static final String PENDING = "PENDING";
    public static final String RUNNING = "RUNNING";
    public static final String DONE = "DONE";
    public static final String CANCELLED = "CANCELLED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String productionNo;
    private String lineName;
    private String shift;
    private Long skuId;
    private String skuName;
    private BigDecimal planQty;
    private BigDecimal outputQty;
    /** 投料量（毛石），仅记录，不进入成品 SKU 库存 */
    private BigDecimal feedQty;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String operator;
    private String remark;
    private LocalDateTime createdAt;
}
