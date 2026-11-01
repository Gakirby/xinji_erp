package com.xinji.erp.modules.auth.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 账号新增 / 修改（员工与客户账号共用） */
@Data
public class UserSaveRequest {

    @NotBlank(message = "请填写登录名")
    private String username;

    /** 新增必填；修改时留空表示不改密码 */
    private String password;

    private String mobile;

    /** EMPLOYEE / CUSTOMER */
    private String userType = "EMPLOYEE";

    /** ADMIN / SALES / FINANCE / WEIGH / DISPATCH / CUSTOMER */
    private String role = "SALES";

    /** 客户账号必填：绑定客户档案 */
    private Long customerId;

    private String employeeName;
}
