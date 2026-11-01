package com.xinji.erp.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.io.Serializable;

/**
 * 客户档案，含授信额度
 */
@Data
@TableName("t_customer")
public class Customer implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String customerNo;
    private String customerName;
    private String contactName;
    private String contactPhone;
    private String address;
    private BigDecimal creditLimit;
    private BigDecimal creditUsed;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
