package com.xinji.erp.modules.order.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class CreateOrderRequest {

    @Valid
    @NotEmpty(message = "请至少选择一个物料")
    private List<OrderItemRequest> items;

    /** DELIVERY 配送 / PICKUP 自提 */
    private String deliveryType = "DELIVERY";

    private String expectedDate;

    private String address;

    private String receiver;

    private String remark;

    /** 员工代客下单时指定客户；客户账号不用传 */
    private Long customerId;

    /** 抢购令牌：走活动价，可为空 */
    private String seckillToken;
}
