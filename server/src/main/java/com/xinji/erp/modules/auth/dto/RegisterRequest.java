package com.xinji.erp.modules.auth.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class RegisterRequest {

    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String mobile;

    @NotBlank(message = "请输入短信验证码")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
    private String code;

    @NotBlank(message = "请设置登录密码")
    @Size(min = 6, max = 20, message = "密码长度 6-20 位")
    private String password;

    @NotBlank(message = "请填写企业名称")
    @Size(max = 120, message = "企业名称不能超过 120 字")
    private String customerName;

    @Size(max = 40, message = "联系人不能超过 40 字")
    private String contactName;

    @Size(max = 200, message = "收货地址不能超过 200 字")
    private String address;
}
