package com.xinji.erp.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.io.Serializable;

/**
 * 登录账号：客户账号与员工账号共用一张表
 */
@Data
@TableName("t_user")
public class User implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String mobile;
    /** CUSTOMER 客户账号 / EMPLOYEE 员工账号 */
    private String userType;
    private Long customerId;
    private String employeeName;
    /** ADMIN/SALES/FINANCE/WEIGH/DISPATCH/CUSTOMER */
    private String role;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
