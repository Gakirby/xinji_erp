package com.xinji.erp.modules.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinji.erp.modules.finance.entity.Receivable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.math.BigDecimal;

@Mapper
public interface ReceivableMapper extends BaseMapper<Receivable> {

    @Select("SELECT IFNULL(SUM(amount - received_amount),0) FROM t_receivable WHERE status <> 'PAID'")
    BigDecimal sumOutstanding();
}
