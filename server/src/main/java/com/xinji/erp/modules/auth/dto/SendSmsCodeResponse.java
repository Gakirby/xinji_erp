package com.xinji.erp.modules.auth.dto;

import lombok.Data;

@Data
public class SendSmsCodeResponse {

    /** 验证码有效期（秒） */
    private int expireIn;

    /**
     * 演示环境回显的验证码。
     * 项目没有接真实短信网关时前端直接填上，方便验收；
     * 接入阿里云/腾讯云短信后把 xinji.sms.mock 改成 false，这个字段就是 null。
     */
    private String devCode;

    private String tip;
}
