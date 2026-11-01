package com.xinji.erp.modules.order.vo;

import com.xinji.erp.modules.order.entity.Order;
import com.xinji.erp.modules.order.entity.OrderItem;
import lombok.Data;

import java.util.List;

@Data
public class OrderVO {

    private Order order;
    private List<OrderItem> items;
    /** 状态中文名，前端直接展示 */
    private String statusLabel;
}
