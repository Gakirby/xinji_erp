package com.xinji.erp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisConfig {

    /**
     * 抢购原子脚本：资格判断 + 一人一单 + 扣库存 + 写 Stream，一次 Redis 内执行完成。
     * 返回值：>=0 剩余库存；-1 重复抢购；-2 未预热；-3 库存不足；-4 超过限购
     */
    @Bean("seckillScript")
    public DefaultRedisScript<Long> seckillScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/seckill.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
