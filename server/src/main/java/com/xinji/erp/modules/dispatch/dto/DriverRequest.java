package com.xinji.erp.modules.dispatch.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** 司机新增 / 修改 */
@Data
public class DriverRequest {

    @NotBlank(message = "请填写司机姓名")
    private String driverName;

    @NotBlank(message = "请填写联系电话")
    private String phone;

    private String idCard;
    private String licenseNo;
}
