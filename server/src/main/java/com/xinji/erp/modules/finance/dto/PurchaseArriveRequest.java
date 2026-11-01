package com.xinji.erp.modules.finance.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 采购到货请求：登记到货量并直接入可售池 */
@Data
public class PurchaseArriveRequest {

    @NotNull(message = "请填写到货量")
    @DecimalMin(value = "0.001", message = "到货量必须大于 0")
    private BigDecimal arrivedQty;

    /** 登记人备注 */
    private String remark;
}
