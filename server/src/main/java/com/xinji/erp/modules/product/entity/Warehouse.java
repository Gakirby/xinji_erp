package com.xinji.erp.modules.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.io.Serializable;

/**
 * 料仓
 */
@Data
@TableName("t_warehouse")
public class Warehouse implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String warehouseNo;
    private String warehouseName;
    private BigDecimal capacity;
    private LocalDateTime createdAt;
}
