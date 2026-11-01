package com.xinji.erp.modules.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.io.Serializable;

/**
 * 秒杀活动
 */
@Data
@TableName("t_seckill_activity")
public class SeckillActivity implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String activityNo;
    private String activityName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** DRAFT/PUBLISHED/RUNNING/ENDED */
    private String status;
    /** NONE/PENDING/DONE */
    private String warmupStatus;
    private String createdBy;
    private LocalDateTime createdAt;
}
