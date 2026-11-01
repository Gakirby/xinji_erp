package com.xinji.erp.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import net.spy.memcached.MemcachedClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 四级缓存：ScopeCaching（请求内） → Caffeine（JVM） → Memcached（跨实例热点） → Redis（共享）。
 *
 * 设计要点：
 *  1. 下层命中后向上回填 ScopeCaching，让同一请求后续重复读直接命中请求内缓存；
 *  2. 空值也缓存（"__NULL__"），防止不存在的 key 反复穿透到数据库；
 *  3. Memcached 是可选层，未部署时自动降级，不影响启动。
 */
@Slf4j
@Component
public class MultiLevelCache {

    private static final String NULL_VALUE = "__NULL__";

    private final Cache<String, String> caffeine;
    private final StringRedisTemplate redis;
    private final ObjectProvider<MemcachedClient> memcachedProvider;

    public MultiLevelCache(Cache<String, String> localCache,
                           StringRedisTemplate redis,
                           ObjectProvider<MemcachedClient> memcachedProvider) {
        this.caffeine = localCache;
        this.redis = redis;
        this.memcachedProvider = memcachedProvider;
    }

    public String get(String key) {
        Object scoped = ScopeCaching.get(key);
        if (scoped != null) {
            return NULL_VALUE.equals(scoped) ? null : String.valueOf(scoped);
        }
        String v = caffeine.getIfPresent(key);
        if (v != null) {
            ScopeCaching.put(key, v);
            return NULL_VALUE.equals(v) ? null : v;
        }
        MemcachedClient mc = memcachedProvider.getIfAvailable();
        if (mc != null) {
            try {
                Object mv = mc.get(key);
                if (mv != null) {
                    String s = String.valueOf(mv);
                    caffeine.put(key, s);
                    ScopeCaching.put(key, s);
                    return NULL_VALUE.equals(s) ? null : s;
                }
            } catch (Exception e) {
                log.debug("memcached 读取失败，降级到 redis: {}", e.getMessage());
            }
        }
        String rv = redis.opsForValue().get(key);
        if (rv != null) {
            caffeine.put(key, rv);
            ScopeCaching.put(key, rv);
            return NULL_VALUE.equals(rv) ? null : rv;
        }
        return null;
    }

    public void put(String key, String value, long ttlSeconds) {
        String v = value == null ? NULL_VALUE : value;
        ScopeCaching.put(key, v);
        caffeine.put(key, v);
        MemcachedClient mc = memcachedProvider.getIfAvailable();
        if (mc != null) {
            try {
                mc.set(key, (int) ttlSeconds, v);
            } catch (Exception e) {
                log.debug("memcached 写入失败: {}", e.getMessage());
            }
        }
        redis.opsForValue().set(key, v, ttlSeconds, TimeUnit.SECONDS);
    }

    /** 空值缓存：短 TTL，避免脏数据长期占位 */
    public void putNull(String key) {
        put(key, NULL_VALUE, 30);
    }

    public void evict(String key) {
        ScopeCaching.evict(key);
        caffeine.invalidate(key);
        MemcachedClient mc = memcachedProvider.getIfAvailable();
        if (mc != null) {
            try {
                mc.delete(key);
            } catch (Exception ignored) {
            }
        }
        redis.delete(key);
    }
}
