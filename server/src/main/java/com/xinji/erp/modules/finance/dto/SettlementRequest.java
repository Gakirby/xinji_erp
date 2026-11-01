package com.xinji.erp.modules.finance.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class SettlementRequest {

    @NotNull(message = "请选择客户")
    private Long customerId;

    @NotNull(message = "请选择账期开始日期")
    private LocalDate periodStart;

    @NotNull(message = "请选择账期结束日期")
    private LocalDate periodEnd;
}
