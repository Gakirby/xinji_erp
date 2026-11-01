package com.xinji.erp.modules.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 车辆档案 */
@Data
@TableName("t_vehicle")
public class Vehicle implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String plateNo;
    private String vehicleType;
    /** 核定载重（吨） */
    private BigDecimal loadCapacity;
    private String ownerName;
    private String ownerPhone;
    /** 保险到期日，调度派车时用来提示证照风险 */
    private LocalDate insuranceExpire;
    private Integer status;
    private LocalDateTime createdAt;
}
