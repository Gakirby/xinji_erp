package com.xinji.erp.modules.weigh.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinji.erp.modules.weigh.entity.WeighTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.math.BigDecimal;

@Mapper
public interface WeighTicketMapper extends BaseMapper<WeighTicket> {

    @Select("SELECT IFNULL(SUM(settle_weight),0) FROM t_weigh_ticket WHERE order_no = #{orderNo} AND status = 'ISSUED'")
    BigDecimal sumSettleWeightByOrder(String orderNo);
}
