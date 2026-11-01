package com.xinji.erp.modules.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinji.erp.modules.finance.entity.Invoice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface InvoiceMapper extends BaseMapper<Invoice> {

    @Select("SELECT IFNULL(SUM(amount),0) FROM t_invoice WHERE status = 'ISSUED'")
    BigDecimal sumIssued();
}
