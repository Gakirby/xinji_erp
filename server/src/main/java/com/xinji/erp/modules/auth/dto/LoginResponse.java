package com.xinji.erp.modules.auth.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LoginResponse {

    private String token;
    private long expiresIn;
    private Long userId;
    private String username;
    private String userType;
    private String role;
    private String displayName;
    private Long customerId;
    private String customerName;
    private BigDecimal creditLimit;
    private BigDecimal creditUsed;
}
