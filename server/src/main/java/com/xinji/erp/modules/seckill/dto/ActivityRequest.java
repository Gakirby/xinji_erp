package com.xinji.erp.modules.seckill.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** 秒杀活动新增 / 修改 */
@Data
public class ActivityRequest {

    @NotBlank(message = "请填写活动名称")
    private String activityName;

    @NotNull(message = "请选择开始时间")
    private LocalDateTime startTime;

    @NotNull(message = "请选择结束时间")
    private LocalDateTime endTime;
}
