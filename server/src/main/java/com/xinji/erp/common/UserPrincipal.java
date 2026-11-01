package com.xinji.erp.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录主体：客户账号与员工账号共用，靠 userType 区分
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal implements Serializable {

    private Long userId;
    private String username;
    /** CUSTOMER / EMPLOYEE */
    private String userType;
    /** ADMIN / SALES / FINANCE / WEIGH / DISPATCH / CUSTOMER */
    private String role;
    /** 客户账号绑定的客户ID，员工为 null */
    private Long customerId;
    private String displayName;

    public boolean isEmployee() {
        return "EMPLOYEE".equals(userType);
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
