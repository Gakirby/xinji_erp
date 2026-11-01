package com.xinji.erp.modules.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinji.erp.modules.seckill.entity.SeckillSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;

@Mapper
public interface SeckillSkuMapper extends BaseMapper<SeckillSku> {

    /** 发放时扣减活动库存：条件更新 + 售罄自动置状态，保证不超卖 */
    @Update("UPDATE t_seckill_sku SET sold_qty = sold_qty + #{qty}, " +
            "status = CASE WHEN sold_qty + #{qty} >= activity_stock THEN 'SOLDOUT' ELSE status END " +
            "WHERE id = #{id} AND activity_stock - sold_qty >= #{qty}")
    int deductStock(@Param("id") Long id, @Param("qty") BigDecimal qty);

    /** 令牌过期或异常时归还活动库存 */
    @Update("UPDATE t_seckill_sku SET sold_qty = sold_qty - #{qty}, " +
            "status = CASE WHEN status = 'SOLDOUT' THEN 'ON' ELSE status END " +
            "WHERE id = #{id} AND sold_qty >= #{qty}")
    int restoreStock(@Param("id") Long id, @Param("qty") BigDecimal qty);
}
