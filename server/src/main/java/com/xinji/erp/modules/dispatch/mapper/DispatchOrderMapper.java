package com.xinji.erp.modules.dispatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinji.erp.modules.dispatch.entity.DispatchOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DispatchOrderMapper extends BaseMapper<DispatchOrder> {

    /** 按状态统计派车单，用于调度看板 */
    @Select("SELECT status, COUNT(*) AS cnt FROM t_dispatch GROUP BY status")
    List<Map<String, Object>> countByStatus();
}
