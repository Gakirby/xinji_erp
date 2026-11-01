package com.xinji.erp.common;

/**
 * 请求级登录上下文。拦截器写入，业务层直接读，afterCompletion 清理。
 */
public final class AuthContext {

    private static final ThreadLocal<UserPrincipal> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(UserPrincipal principal) {
        HOLDER.set(principal);
    }

    public static UserPrincipal get() {
        return HOLDER.get();
    }

    public static UserPrincipal require() {
        UserPrincipal p = HOLDER.get();
        if (p == null) {
            throw BizException.unauthorized("未登录或登录已过期");
        }
        return p;
    }

    public static Long userId() {
        return require().getUserId();
    }

    public static Long customerId() {
        UserPrincipal p = require();
        if (p.getCustomerId() == null) {
            throw BizException.forbidden("当前账号未绑定客户档案");
        }
        return p.getCustomerId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
