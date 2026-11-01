package com.xinji.erp.modules.auth;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinji.erp.common.BizException;
import com.xinji.erp.modules.auth.dto.UserSaveRequest;
import com.xinji.erp.modules.auth.entity.Customer;
import com.xinji.erp.modules.auth.entity.User;
import com.xinji.erp.modules.auth.mapper.CustomerMapper;
import com.xinji.erp.modules.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 账号管理：员工账号 + 客户账号。
 *
 * 客户账号必须绑定 customer_id —— 网页商城和小程序登录的是同一个账号，
 * 订单、对账、授信都挂在这一个客户主体上，不会出现「网页下一单、小程序又下一单」两套数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    public static final List<String> EMPLOYEE_ROLES =
            Arrays.asList("ADMIN", "SALES", "FINANCE", "WEIGH", "DISPATCH");

    private final UserMapper userMapper;
    private final CustomerMapper customerMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public IPage<User> page(String userType, String keyword, long page, long size) {
        return userMapper.selectPage(new Page<>(page, size),
                Wrappers.<User>lambdaQuery()
                        .eq(userType != null && !userType.isEmpty(), User::getUserType, userType)
                        .and(keyword != null && !keyword.isEmpty(), w -> w
                                .like(User::getUsername, keyword)
                                .or().like(User::getEmployeeName, keyword)
                                .or().like(User::getMobile, keyword))
                        .orderByAsc(User::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public User create(UserSaveRequest req) {
        if (req.getPassword() == null || req.getPassword().length() < 6) {
            throw new BizException("初始密码至少 6 位");
        }
        User dup = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, req.getUsername()));
        if (dup != null) {
            throw new BizException("登录名 " + req.getUsername() + " 已存在");
        }
        User u = new User();
        u.setUsername(req.getUsername().trim());
        u.setPassword(encoder.encode(req.getPassword()));
        u.setMobile(req.getMobile());
        u.setUserType(normalizeType(req.getUserType()));
        u.setRole(normalizeRole(u.getUserType(), req.getRole()));
        u.setEmployeeName(req.getEmployeeName());
        u.setCustomerId(resolveCustomerId(u.getUserType(), req.getCustomerId()));
        u.setStatus(1);
        userMapper.insert(u);
        log.info("账号创建: {} 类型={} 角色={} 绑定客户={}", u.getUsername(), u.getUserType(),
                u.getRole(), u.getCustomerId());
        return u;
    }

    @Transactional(rollbackFor = Exception.class)
    public User update(Long id, UserSaveRequest req) {
        User u = require(id);
        User dup = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, req.getUsername()));
        if (dup != null && !dup.getId().equals(id)) {
            throw new BizException("登录名 " + req.getUsername() + " 已被占用");
        }
        u.setUsername(req.getUsername().trim());
        u.setMobile(req.getMobile());
        u.setEmployeeName(req.getEmployeeName());
        if (req.getUserType() != null && !req.getUserType().equals(u.getUserType())) {
            throw new BizException("账号类型不允许修改，请新建账号");
        }
        if (req.getRole() != null) {
            u.setRole(normalizeRole(u.getUserType(), req.getRole()));
        }
        if ("CUSTOMER".equals(u.getUserType())) {
            u.setCustomerId(resolveCustomerId(u.getUserType(), req.getCustomerId()));
        }
        if (req.getPassword() != null && !req.getPassword().isEmpty()) {
            if (req.getPassword().length() < 6) {
                throw new BizException("密码至少 6 位");
            }
            u.setPassword(encoder.encode(req.getPassword()));
        }
        userMapper.updateById(u);
        return u;
    }

    public User resetPassword(Long id, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException("密码至少 6 位");
        }
        User u = require(id);
        u.setPassword(encoder.encode(newPassword));
        userMapper.updateById(u);
        log.info("重置密码: {}", u.getUsername());
        return u;
    }

    public User toggle(Long id, Integer status) {
        User u = require(id);
        if ("admin".equalsIgnoreCase(u.getUsername()) && status != null && status == 0) {
            throw new BizException("内置管理员账号不允许停用");
        }
        u.setStatus(status == null ? (u.getStatus() != null && u.getStatus() == 1 ? 0 : 1) : status);
        userMapper.updateById(u);
        return u;
    }

    /** 账号概览：按类型统计 + 角色字典 */
    public Map<String, Object> overview() {
        Map<String, Object> m = new HashMap<>();
        m.put("employeeCount", userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .eq(User::getUserType, "EMPLOYEE")));
        m.put("customerCount", userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .eq(User::getUserType, "CUSTOMER")));
        m.put("disabledCount", userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .eq(User::getStatus, 0)));
        m.put("employeeRoles", EMPLOYEE_ROLES);
        m.put("customerAccounts", userMapper.selectList(Wrappers.<User>lambdaQuery()
                .eq(User::getUserType, "CUSTOMER")));
        return m;
    }

    private String normalizeType(String type) {
        return "CUSTOMER".equalsIgnoreCase(type) ? "CUSTOMER" : "EMPLOYEE";
    }

    private String normalizeRole(String userType, String role) {
        if ("CUSTOMER".equals(userType)) {
            return "CUSTOMER";
        }
        String r = role == null || role.isEmpty() ? "SALES" : role.toUpperCase();
        if (!EMPLOYEE_ROLES.contains(r)) {
            throw new BizException("非法的员工角色: " + role);
        }
        return r;
    }

    private Long resolveCustomerId(String userType, Long customerId) {
        if (!"CUSTOMER".equals(userType)) {
            return null;
        }
        if (customerId == null) {
            throw new BizException("客户账号必须绑定客户档案");
        }
        Customer c = customerMapper.selectById(customerId);
        if (c == null) {
            throw BizException.notFound("客户档案不存在: " + customerId);
        }
        if (c.getStatus() != null && c.getStatus() == 0) {
            throw new BizException("客户「" + c.getCustomerName() + "」已停用，不能开账号");
        }
        return customerId;
    }

    private User require(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) {
            throw BizException.notFound("账号不存在: " + id);
        }
        return u;
    }
}
