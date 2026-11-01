package com.xinji.erp.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import net.spy.memcached.MemcachedClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class CacheConfig {

    @Value("${xinji.cache.caffeine-max-size:2000}")
    private long maxSize;

    @Value("${xinji.cache.caffeine-ttl-seconds:120}")
    private long ttlSeconds;

    @Bean("localCache")
    public Cache<String, String> localCache() {
        return Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Memcached 是可选层。没部署时保持 xinji.cache.memcached-enabled=false，
     * 系统自动跳过这一层，其余三级缓存照常工作。
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "xinji.cache.memcached-enabled", havingValue = "true")
    public MemcachedClient memcachedClient(
            @Value("${xinji.cache.memcached-host:127.0.0.1}") String host,
            @Value("${xinji.cache.memcached-port:11211}") int port) throws Exception {
        log.info("启用 Memcached 热点缓存层: {}:{}", host, port);
        return new MemcachedClient(new InetSocketAddress(host, port));
    }
}
