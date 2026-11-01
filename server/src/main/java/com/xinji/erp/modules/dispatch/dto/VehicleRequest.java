package com.xinji.erp.modules.dispatch.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 车辆新增 / 修改 */
@Data
public class VehicleRequest {

    @NotBlank(message = "请填写车牌号")
    private String plateNo;

    @NotBlank(message = "请填写车型")
    private String vehicleType;

    @NotNull(message = "请填写核定载重")
    @DecimalMin(value = "0.001", message = "核定载重必须大于 0")
    private BigDecimal loadCapacity;

    private String ownerName;
    private String ownerPhone;

    /** 保险到期日，为空表示未登记 */
    private LocalDate insuranceExpire;
}
