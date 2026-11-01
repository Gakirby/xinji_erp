package com.xinji.erp.modules.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 派车单。
 * 订单审核后创建派车单，订单状态同步推进到「已派车」；
 * 出厂过磅后派车单置 SHIPPED，与磅单、订单共用 orderNo 串联。
 */
@Data
@TableName("t_dispatch")
public class DispatchOrder implements Serializable {

    public static final String PENDING = "PENDING";
    public static final String LOADING = "LOADING";
    public static final String SHIPPED = "SHIPPED";
    public static final String DONE = "DONE";
    public static final String CANCELLED = "CANCELLED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String dispatchNo;
    private String orderNo;
    private String customerName;
    private Long vehicleId;
    private String plateNo;
    private Long driverId;
    private String driverName;
    private String driverPhone;
    private BigDecimal planQty;
    private LocalDateTime planTime;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
