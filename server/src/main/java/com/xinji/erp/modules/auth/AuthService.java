package com.xinji.erp.modules.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xinji.erp.common.AuthContext;
import com.xinji.erp.common.BizException;
import com.xinji.erp.common.JwtUtil;
import com.xinji.erp.common.UserPrincipal;
import com.xinji.erp.modules.auth.dto.LoginRequest;
import com.xinji.erp.modules.auth.dto.LoginResponse;
import com.xinji.erp.modules.auth.entity.Customer;
import com.xinji.erp.modules.auth.entity.User;
import com.xinji.erp.modules.auth.mapper.CustomerMapper;
import com.xinji.erp.modules.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final CustomerMapper customerMapper;
    private final JwtUtil jwtUtil;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public LoginResponse login(LoginRequest req) {
        return login(req.getUsername(), req.getPassword());
    }

    /**
     * 账号密码登录。账号名先按 username 查，查不到再按手机号查 ——
     * 手机号注册的客户账号 username 本身就是手机号，这条兜底主要给
     * 后台手工建的老账号用（它们 username 是 yatai 这类简写，但录了手机号）。
     */
    public LoginResponse login(String account, String password) {
        String key = account == null ? "" : account.trim();
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, key)
                .last("limit 1"));
        if (user == null) {
            user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                    .eq(User::getMobile, key)
                    .last("limit 1"));
        }
        if (user == null) {
            throw BizException.unauthorized("账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw BizException.forbidden("账号已停用，请联系管理员");
        }
        if (!encoder.matches(password, user.getPassword())) {
            throw BizException.unauthorized("账号或密码错误");
        }

        UserPrincipal principal = new UserPrincipal();
        principal.setUserId(user.getId());
        principal.setUsername(user.getUsername());
        principal.setUserType(user.getUserType());
        principal.setRole(user.getRole());
        principal.setCustomerId(user.getCustomerId());
        // 客户账号的显示名统一用公司名：同一个 customer_id 在网页商城和小程序里看到的是同一个身份
        String displayName = user.getEmployeeName();
        if (!"EMPLOYEE".equals(user.getUserType()) && user.getCustomerId() != null) {
            Customer own = customerMapper.selectById(user.getCustomerId());
            displayName = own == null ? user.getUsername() : own.getCustomerName();
        }
        principal.setDisplayName(displayName == null ? user.getUsername() : displayName);

        LoginResponse resp = new LoginResponse();
        resp.setToken(jwtUtil.create(principal));
        resp.setExpiresIn(jwtUtil.getExpireMinutes() * 60);
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setUserType(user.getUserType());
        resp.setRole(user.getRole());
        resp.setDisplayName(principal.getDisplayName());

        if (user.getCustomerId() != null) {
            Customer c = customerMapper.selectById(user.getCustomerId());
            if (c != null) {
                resp.setCustomerId(c.getId());
                resp.setCustomerName(c.getCustomerName());
                resp.setCreditLimit(c.getCreditLimit());
                resp.setCreditUsed(c.getCreditUsed());
            }
        }
        log.info("登录成功: {} ({})", user.getUsername(), user.getUserType());
        return resp;
    }

    public LoginResponse current() {
        UserPrincipal p = AuthContext.require();
        LoginResponse resp = new LoginResponse();
        resp.setUserId(p.getUserId());
        resp.setUsername(p.getUsername());
        resp.setUserType(p.getUserType());
        resp.setRole(p.getRole());
        resp.setDisplayName(p.getDisplayName());
        resp.setCustomerId(p.getCustomerId());
        if (p.getCustomerId() != null) {
            Customer c = customerMapper.selectById(p.getCustomerId());
            if (c != null) {
                resp.setCustomerName(c.getCustomerName());
                resp.setCreditLimit(c.getCreditLimit());
                resp.setCreditUsed(c.getCreditUsed());
            }
        }
        return resp;
    }
}
