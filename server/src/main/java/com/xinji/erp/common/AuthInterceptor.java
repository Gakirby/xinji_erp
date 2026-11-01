package com.xinji.erp.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 鉴权拦截器。
 * 注意：前端的路由隐藏不是安全边界，真正的边界在这里 ——
 *   /api/admin/**   只有 EMPLOYEE 能进
 *   /api/client/**  客户或员工（代客下单）能进，但业务层只能操作自己 customerId 的数据
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public AuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        String header = request.getHeader("Authorization");
        String token = null;
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7).trim();
        }
        if (token == null || token.isEmpty()) {
            throw BizException.unauthorized("缺少登录凭证，请先登录");
        }
        UserPrincipal principal;
        try {
            principal = jwtUtil.parse(token);
        } catch (Exception e) {
            throw BizException.unauthorized("登录已过期，请重新登录");
        }
        AuthContext.set(principal);

        if (uri.startsWith("/api/admin")) {
            if (!principal.isEmployee()) {
                throw BizException.forbidden("无权限访问管理后台接口");
            }
        }
        if (uri.startsWith("/api/client")) {
            if (!"CUSTOMER".equals(principal.getUserType()) && !principal.isEmployee()) {
                throw BizException.forbidden("账号类型不合法");
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
    }
}
