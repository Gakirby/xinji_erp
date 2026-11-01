package com.xinji.erp.modules.seckill.dto;

import lombok.Data;

@Data
public class GrabResult {

    /** true 表示抢到了，tokenNo 有效；false 表示没抢到，reason 说明原因 */
    private boolean success;
    private String tokenNo;
    private String reason;
    private Long seckillSkuId;
    /** 扣减后剩余活动库存，-1 表示未知 */
    private long remainStock = -1;
}
