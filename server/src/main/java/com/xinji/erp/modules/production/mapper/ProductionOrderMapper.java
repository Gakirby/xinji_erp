package com.xinji.erp.modules.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinji.erp.modules.production.entity.ProductionOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProductionOrderMapper extends BaseMapper<ProductionOrder> {

    /** 按物料汇总本月产量 */
    @Select("SELECT sku_name AS skuName, IFNULL(SUM(output_qty),0) AS outputQty " +
            "FROM t_production_order WHERE status='DONE' " +
            "AND DATE_FORMAT(created_at,'%Y-%m') = DATE_FORMAT(NOW(),'%Y-%m') " +
            "GROUP BY sku_id, sku_name ORDER BY outputQty DESC")
    List<Map<String, Object>> monthOutputBySku();
}
