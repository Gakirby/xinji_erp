package com.xinji.erp.modules.dispatch.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 派车请求 */
@Data
public class DispatchRequest {

    @NotBlank(message = "请填写订单号")
    private String orderNo;

    @NotNull(message = "请选择车辆")
    private Long vehicleId;

    @NotNull(message = "请选择司机")
    private Long driverId;

    @NotNull(message = "请填写计划装载吨数")
    @DecimalMin(value = "0.001", message = "计划装载吨数必须大于 0")
    private BigDecimal planQty;

    /** 计划发车时间 */
    private LocalDateTime planTime;

    private String remark;
}
