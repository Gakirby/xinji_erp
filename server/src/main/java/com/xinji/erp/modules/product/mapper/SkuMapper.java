package com.xinji.erp.modules.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinji.erp.modules.product.entity.Sku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;

@Mapper
public interface SkuMapper extends BaseMapper<Sku> {

    /** 下单冻结：条件更新保证不超卖 */
    @Update("UPDATE t_sku SET stock_qty = stock_qty - #{qty}, frozen_qty = frozen_qty + #{qty} " +
            "WHERE id = #{id} AND stock_qty >= #{qty}")
    int freeze(@Param("id") Long id, @Param("qty") BigDecimal qty);

    /** 取消订单解冻 */
    @Update("UPDATE t_sku SET stock_qty = stock_qty + #{qty}, frozen_qty = frozen_qty - #{qty} " +
            "WHERE id = #{id} AND frozen_qty >= #{qty}")
    int unfreeze(@Param("id") Long id, @Param("qty") BigDecimal qty);

    /** 出库核销：冻结量转为实际出库，不再回到可售 */
    @Update("UPDATE t_sku SET frozen_qty = frozen_qty - #{qty} " +
            "WHERE id = #{id} AND frozen_qty >= #{qty}")
    int consumeFrozen(@Param("id") Long id, @Param("qty") BigDecimal qty);

    /** 活动划拨：从销售可售池划出，进入活动池 */
    @Update("UPDATE t_sku SET stock_qty = stock_qty - #{qty}, activity_alloc_qty = activity_alloc_qty + #{qty} " +
            "WHERE id = #{id} AND stock_qty >= #{qty}")
    int allocToActivity(@Param("id") Long id, @Param("qty") BigDecimal qty);

    /** 活动结束回拨：剩余活动库存回到销售可售池 */
    @Update("UPDATE t_sku SET stock_qty = stock_qty + #{qty}, activity_alloc_qty = activity_alloc_qty - #{qty} " +
            "WHERE id = #{id} AND activity_alloc_qty >= #{qty}")
    int returnFromActivity(@Param("id") Long id, @Param("qty") BigDecimal qty);

    /** 采购入库 */
    @Update("UPDATE t_sku SET stock_qty = stock_qty + #{qty} WHERE id = #{id}")
    int inbound(@Param("id") Long id, @Param("qty") BigDecimal qty);

    /** 令牌过期：把未成交的量还给活动池 */
    @Update("UPDATE t_sku SET activity_alloc_qty = activity_alloc_qty + #{qty} WHERE id = #{id}")
    int returnActivity(@Param("id") Long id, @Param("qty") BigDecimal qty);

    /** 秒杀成交后从活动池核销（不改可售，只减活动划拨并记出库） */
    @Update("UPDATE t_sku SET activity_alloc_qty = activity_alloc_qty - #{qty} " +
            "WHERE id = #{id} AND activity_alloc_qty >= #{qty}")
    int consumeActivity(@Param("id") Long id, @Param("qty") BigDecimal qty);
}
