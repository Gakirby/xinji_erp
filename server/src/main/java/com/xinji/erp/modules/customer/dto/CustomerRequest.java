package com.xinji.erp.modules.customer.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

/** 客户新增 / 修改 */
@Data
public class CustomerRequest {

    @NotBlank(message = "请填写客户名称")
    private String customerName;

    private String contactName;
    private String contactPhone;
    private String address;

    @DecimalMin(value = "0", message = "授信额度不能为负数")
    private BigDecimal creditLimit;
}
