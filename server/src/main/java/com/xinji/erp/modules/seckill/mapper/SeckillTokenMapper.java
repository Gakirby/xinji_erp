package com.xinji.erp.modules.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinji.erp.modules.seckill.entity.SeckillToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;

@Mapper
public interface SeckillTokenMapper extends BaseMapper<SeckillToken> {

    /** 令牌核销：只有 ISSUED 状态才能转 USED，天然幂等 */
    @Update("UPDATE t_seckill_token SET status = 'USED', order_no = #{orderNo} " +
            "WHERE token_no = #{tokenNo} AND status = 'ISSUED'")
    int useToken(@Param("tokenNo") String tokenNo, @Param("orderNo") String orderNo);

    /** 过期处理 */
    @Update("UPDATE t_seckill_token SET status = 'EXPIRED' " +
            "WHERE token_no = #{tokenNo} AND status = 'ISSUED' AND expire_time < #{now}")
    int expireToken(@Param("tokenNo") String tokenNo, @Param("now") LocalDateTime now);
}
