package com.xinji.erp.modules.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 司机档案 */
@Data
@TableName("t_driver")
public class Driver implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String driverName;
    private String phone;
    /** 身份证号，仓储层已脱敏 */
    private String idCard;
    private String licenseNo;
    private Integer status;
    private LocalDateTime createdAt;
}
