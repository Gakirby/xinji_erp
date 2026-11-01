package com.xinji.erp.modules.stock.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.io.Serializable;

/**
 * 库存流水。任何一次库存变动都必须留一条，这是"数据不割裂"的底账
 */
@Data
@TableName("t_stock_flow")
public class StockFlow implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String flowNo;
    private Long skuId;
    private Long warehouseId;
    /** INBOUND/OUTBOUND/FREEZE/UNFREEZE/ALLOC/RETURN/CHECK */
    private String bizType;
    private BigDecimal qty;
    private BigDecimal beforeQty;
    private BigDecimal afterQty;
    private String bizNo;
    private String remark;
    private String operator;
    private LocalDateTime createdAt;
}
