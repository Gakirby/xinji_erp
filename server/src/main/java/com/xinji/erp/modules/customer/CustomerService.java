package com.xinji.erp.modules.customer;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinji.erp.common.BizException;
import com.xinji.erp.common.IdGen;
import com.xinji.erp.modules.auth.entity.Customer;
import com.xinji.erp.modules.auth.entity.User;
import com.xinji.erp.modules.auth.mapper.CustomerMapper;
import com.xinji.erp.modules.auth.mapper.UserMapper;
import com.xinji.erp.modules.customer.dto.CustomerRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 客户与授信。
 *
 * 授信口径：credit_used 由下单/取消实时维护（新增订单占用、取消释放），
 * 这里只负责额度调整和余额核对，避免两处各算一套导致对不上。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerMapper customerMapper;
    private final UserMapper userMapper;

    public IPage<Customer> page(String keyword, Integer status, long page, long size) {
        return customerMapper.selectPage(new Page<>(page, size),
                Wrappers.<Customer>lambdaQuery()
                        .eq(status != null, Customer::getStatus, status)
                        .and(keyword != null && !keyword.isEmpty(), w -> w
                                .like(Customer::getCustomerName, keyword)
                                .or().like(Customer::getCustomerNo, keyword)
                                .or().like(Customer::getContactName, keyword))
                        .orderByAsc(Customer::getId));
    }

    public List<Customer> list() {
        return customerMapper.selectList(Wrappers.<Customer>lambdaQuery()
                .orderByAsc(Customer::getId));
    }

    /** 客户详情：带授信占用率与是否有登录账号 */
    public Map<String, Object> detail(Long id) {
        Customer c = require(id);
        Map<String, Object> m = new HashMap<>();
        m.put("customer", c);
        m.put("creditLimit", nz(c.getCreditLimit()));
        m.put("creditUsed", nz(c.getCreditUsed()));
        m.put("creditAvailable", nz(c.getCreditLimit()).subtract(nz(c.getCreditUsed())));
        m.put("creditUsageRate", nz(c.getCreditLimit()).compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : nz(c.getCreditUsed()).divide(nz(c.getCreditLimit()), 4, BigDecimal.ROUND_HALF_UP));
        User account = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getCustomerId, id)
                .eq(User::getUserType, "CUSTOMER")
                .last("LIMIT 1"));
        m.put("hasAccount", account != null);
        m.put("loginName", account == null ? null : account.getUsername());
        return m;
    }

    @Transactional(rollbackFor = Exception.class)
    public Customer save(Long id, CustomerRequest req) {
        Customer exists = customerMapper.selectOne(Wrappers.<Customer>lambdaQuery()
                .eq(Customer::getCustomerName, req.getCustomerName()));
        if (exists != null && !exists.getId().equals(id)) {
            throw new BizException("客户「" + req.getCustomerName() + "」已存在");
        }
        Customer c = id == null ? new Customer() : require(id);
        c.setCustomerName(req.getCustomerName());
        c.setContactName(req.getContactName());
        c.setContactPhone(req.getContactPhone());
        c.setAddress(req.getAddress());
        if (req.getCreditLimit() != null) {
            c.setCreditLimit(req.getCreditLimit());
        }
        if (id == null) {
            c.setCustomerNo(IdGen.customerNo());
            c.setCreditUsed(BigDecimal.ZERO);
            c.setStatus(1);
            customerMapper.insert(c);
        } else {
            customerMapper.updateById(c);
        }
        log.info("客户档案保存: {} {}", c.getCustomerNo(), c.getCustomerName());
        return c;
    }

    /** 调整授信额度；下调后不能低于已占用额度 */
    public Customer adjustCredit(Long id, BigDecimal creditLimit) {
        if (creditLimit == null || creditLimit.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("授信额度不能为负数");
        }
        Customer c = require(id);
        if (creditLimit.compareTo(nz(c.getCreditUsed())) < 0) {
            throw new BizException("授信额度不能低于已占用额度 " + nz(c.getCreditUsed())
                    + " 元，请先结清或提高额度");
        }
        c.setCreditLimit(creditLimit);
        customerMapper.updateById(c);
        return c;
    }

    public Customer toggle(Long id, Integer status) {
        Customer c = require(id);
        c.setStatus(status == null ? (c.getStatus() != null && c.getStatus() == 1 ? 0 : 1) : status);
        customerMapper.updateById(c);
        return c;
    }

    /** 额度预警：已用超过授信 80% */
    public List<Map<String, Object>> creditWarnings() {
        return list().stream()
                .filter(c -> nz(c.getCreditLimit()).compareTo(BigDecimal.ZERO) > 0)
                .map(c -> {
                    BigDecimal rate = nz(c.getCreditUsed())
                            .divide(nz(c.getCreditLimit()), 4, BigDecimal.ROUND_HALF_UP);
                    Map<String, Object> m = new HashMap<>();
                    m.put("customerId", c.getId());
                    m.put("customerName", c.getCustomerName());
                    m.put("creditLimit", nz(c.getCreditLimit()));
                    m.put("creditUsed", nz(c.getCreditUsed()));
                    m.put("usageRate", rate);
                    m.put("status", rate.compareTo(BigDecimal.ONE) >= 0 ? "已超额"
                            : rate.compareTo(new BigDecimal("0.8")) >= 0 ? "临界" : "正常");
                    return m;
                })
                .filter(m -> !"正常".equals(m.get("status")))
                .collect(Collectors.toList());
    }

    private Customer require(Long id) {
        Customer c = customerMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("客户不存在: " + id);
        }
        return c;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
