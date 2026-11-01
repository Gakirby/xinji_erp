package com.xinji.erp.common.cache;

import java.util.HashMap;
import java.util.Map;

/**
 * 第一级：请求内缓存（Scope）。
 * 同一次请求里重复读取同一个 key 直接命中，不再穿透到下游缓存。
 */
public final class ScopeCaching {

    private static final ThreadLocal<Map<String, Object>> CACHE = new ThreadLocal<>();

    private ScopeCaching() {
    }

    public static void begin() {
        CACHE.set(new HashMap<>(8));
    }

    public static void end() {
        CACHE.remove();
    }

    public static Object get(String key) {
        Map<String, Object> m = CACHE.get();
        return m == null ? null : m.get(key);
    }

    public static void put(String key, Object value) {
        Map<String, Object> m = CACHE.get();
        if (m != null) {
            m.put(key, value);
        }
    }

    public static void evict(String key) {
        Map<String, Object> m = CACHE.get();
        if (m != null) {
            m.remove(key);
        }
    }
}
