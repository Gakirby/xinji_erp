package com.xinji.erp.common;

import com.xinji.erp.common.cache.ScopeCaching;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 保证每个请求都有独立的请求内缓存作用域，避免 ThreadLocal 泄漏。
 */
@Component
public class ScopeCacheInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        ScopeCaching.begin();
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        ScopeCaching.end();
    }
}
