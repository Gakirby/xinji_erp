package com.xinji.erp.modules.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinji.erp.modules.stock.entity.StockFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.math.BigDecimal;
import java.util.Map;

@Mapper
public interface StockFlowMapper extends BaseMapper<StockFlow> {

    /** 按业务类型汇总出入库量 */
    @Select("SELECT biz_type AS bizType, IFNULL(SUM(qty),0) AS qty FROM t_stock_flow GROUP BY biz_type")
    java.util.List<Map<String, Object>> sumByBizType();

    @Select("SELECT IFNULL(SUM(qty),0) FROM t_stock_flow WHERE biz_type = 'INBOUND'")
    BigDecimal sumInbound();
}
