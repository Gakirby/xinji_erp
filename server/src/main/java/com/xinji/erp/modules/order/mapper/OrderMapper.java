package com.xinji.erp.modules.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinji.erp.modules.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.math.BigDecimal;
import java.util.Map;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /** 按状态统计订单数与吨数，工作台用 */
    @Select("SELECT status AS status, COUNT(*) AS cnt, IFNULL(SUM(total_qty),0) AS qty " +
            "FROM t_order WHERE status <> 'CANCELLED' GROUP BY status")
    java.util.List<Map<String, Object>> countByStatus();

    /** 客户本月累计未回款金额（授信校验用） */
    @Select("SELECT IFNULL(SUM(pay_amount),0) FROM t_order " +
            "WHERE customer_id = #{customerId} AND status IN ('AUDITED','DISPATCHED','SHIPPED','SIGNED','SETTLED','INVOICED')")
    BigDecimal sumUnpaidAmount(@Param("customerId") Long customerId);
}
