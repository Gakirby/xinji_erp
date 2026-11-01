package com.xinji.erp.modules.finance.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 开票申请 */
@Data
public class InvoiceRequest {

    /** 从应收单开票，与 settlementId 二选一 */
    private Long receivableId;

    /** 从对账批次开票 */
    private Long settlementId;

    /** VAT_SPECIAL 专票 / VAT_NORMAL 普票 */
    private String invoiceType = "VAT_SPECIAL";

    /** 价税合计，传空则按应收/对账金额全额开 */
    @DecimalMin(value = "0.01", message = "开票金额必须大于 0")
    private BigDecimal amount;

    /** 税率，默认 13% */
    private BigDecimal taxRate;

    private String remark;

    @NotNull(message = "请输入开票金额或选择单据")
    private Boolean fromBill = Boolean.TRUE;
}
