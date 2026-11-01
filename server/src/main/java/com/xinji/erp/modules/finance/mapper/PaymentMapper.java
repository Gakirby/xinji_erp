package com.xinji.erp.modules.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinji.erp.modules.finance.entity.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {

    @Select("SELECT IFNULL(SUM(amount),0) FROM t_payment")
    BigDecimal sumAll();

    @Select("SELECT IFNULL(SUM(amount),0) FROM t_payment WHERE DATE_FORMAT(pay_time,'%Y-%m') = DATE_FORMAT(NOW(),'%Y-%m')")
    BigDecimal sumThisMonth();
}
