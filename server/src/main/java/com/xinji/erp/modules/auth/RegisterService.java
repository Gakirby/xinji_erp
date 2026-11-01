package com.xinji.erp.modules.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xinji.erp.common.BizException;
import com.xinji.erp.common.IdGen;
import com.xinji.erp.modules.auth.dto.LoginResponse;
import com.xinji.erp.modules.auth.dto.RegisterRequest;
import com.xinji.erp.modules.auth.dto.SendSmsCodeResponse;
import com.xinji.erp.modules.auth.entity.Customer;
import com.xinji.erp.modules.auth.entity.User;
import com.xinji.erp.modules.auth.mapper.CustomerMapper;
import com.xinji.erp.modules.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 客户自助注册：手机号 + 短信验证码 → 建客户档案 → 建登录账号 → 直接签发 token。
 *
 * <p>验证码存在 Redis（5 分钟），发送频控 60 秒，连续错 5 次作废。
 * 没有接真实短信网关时（xinji.sms.mock=true）验证码直接写在接口返回里，
 * 前端会自动填上，方便演示；接网关后把配置改成 false 即可，业务代码不用动。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterService {

    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /** 验证码缓存 */
    private static final String CODE_KEY = "reg:sms:code:";
    /** 发送频控（60 秒） */
    private static final String LIMIT_KEY = "reg:sms:limit:";
    /** 验证码错误次数 */
    private static final String FAIL_KEY = "reg:sms:fail:";

    private static final int CODE_TTL_SECONDS = 300;
    private static final int RESEND_SECONDS = 60;
    private static final int MAX_FAIL = 5;

    private final StringRedisTemplate redis;
    private final UserMapper userMapper;
    private final CustomerMapper customerMapper;
    private final AuthService authService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 演示模式：不真发短信，把验证码回显给前端 */
    @Value("${xinji.sms.mock:true}")
    private boolean mockSms;

    // ------------------------------------------------------------------ 发送验证码

    public SendSmsCodeResponse sendCode(String rawMobile) {
        String mobile = normalize(rawMobile);
        if (isRegistered(mobile)) {
            throw new BizException("该手机号已注册，请直接登录");
        }

        String limitKey = LIMIT_KEY + mobile;
        Boolean allowed = redis.opsForValue().setIfAbsent(limitKey, "1", RESEND_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(allowed)) {
            Long ttl = redis.getExpire(limitKey, TimeUnit.SECONDS);
            long wait = ttl == null || ttl <= 0 ? RESEND_SECONDS : ttl;
            throw new BizException("发送过于频繁，请 " + wait + " 秒后再试");
        }

        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
        redis.opsForValue().set(CODE_KEY + mobile, code, CODE_TTL_SECONDS, TimeUnit.SECONDS);
        redis.delete(FAIL_KEY + mobile);

        // === 接真实短信网关的位置 ===
        // 阿里云：dysmsapi SendSmsRequest(templateCode, phoneNumbers, templateParam={"code":code})
        // 腾讯云：sms SendSmsRequest(TemplateId, PhoneNumberSet, TemplateParamSet=[code])
        // 接入后把 xinji.sms.mock 改成 false，并把下面这行打印替换成网关调用
        log.info("[注册验证码] 手机号 {} 验证码 {}（{} 秒内有效）", mobile, code, CODE_TTL_SECONDS);

        SendSmsCodeResponse resp = new SendSmsCodeResponse();
        resp.setExpireIn(CODE_TTL_SECONDS);
        if (mockSms) {
            resp.setDevCode(code);
            resp.setTip("演示环境不真实下发短信，验证码已自动填入，5 分钟内有效");
        } else {
            resp.setTip("验证码已发送至 " + mask(mobile) + "，5 分钟内有效");
        }
        return resp;
    }

    // ------------------------------------------------------------------ 注册

    @Transactional(rollbackFor = Exception.class)
    public LoginResponse register(RegisterRequest req) {
        String mobile = normalize(req.getMobile());
        if (isRegistered(mobile)) {
            throw new BizException("该手机号已注册，请直接登录");
        }

        String codeKey = CODE_KEY + mobile;
        String cached = redis.opsForValue().get(codeKey);
        if (cached == null) {
            throw new BizException("验证码已过期，请重新获取");
        }
        if (!cached.equals(req.getCode().trim())) {
            throw new BizException("验证码不正确，还可尝试 " + remainTry(mobile) + " 次");
        }
        // 验证码一次性，用过即焚
        redis.delete(codeKey);
        redis.delete(FAIL_KEY + mobile);
        redis.delete(LIMIT_KEY + mobile);

        // 1) 客户档案：新注册默认无授信额度，由运营在后台「客户授信」里调整
        Customer customer = new Customer();
        customer.setCustomerNo(IdGen.customerNo());
        customer.setCustomerName(req.getCustomerName().trim());
        customer.setContactName(trimToNull(req.getContactName()));
        customer.setContactPhone(mobile);
        customer.setAddress(trimToNull(req.getAddress()));
        customer.setCreditLimit(BigDecimal.ZERO);
        customer.setCreditUsed(BigDecimal.ZERO);
        customer.setStatus(1);
        customerMapper.insert(customer);

        // 2) 登录账号：账号名就是手机号，客户也可以用手机号登录
        User user = new User();
        user.setUsername(mobile);
        user.setPassword(encoder.encode(req.getPassword()));
        user.setMobile(mobile);
        user.setUserType("CUSTOMER");
        user.setRole("CUSTOMER");
        user.setCustomerId(customer.getId());
        user.setStatus(1);
        userMapper.insert(user);

        log.info("客户注册成功：{} / {}（客户ID={}，账号ID={}）",
                customer.getCustomerName(), mask(mobile), customer.getId(), user.getId());

        // 3) 注册即登录，省掉再输一次账号密码
        return authService.login(mobile, req.getPassword());
    }

    // ------------------------------------------------------------------ 校验/小工具

    public boolean isRegistered(String rawMobile) {
        String mobile = normalize(rawMobile);
        return userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .eq(User::getMobile, mobile)
                .or()
                .eq(User::getUsername, mobile)) > 0;
    }

    /** 校验验证码错误次数，超过阈值直接作废本次验证码 */
    private long remainTry(String mobile) {
        Long fails = redis.opsForValue().increment(FAIL_KEY + mobile);
        redis.expire(FAIL_KEY + mobile, CODE_TTL_SECONDS, TimeUnit.SECONDS);
        long used = fails == null ? 1 : fails;
        if (used >= MAX_FAIL) {
            redis.delete(CODE_KEY + mobile);
            throw new BizException("验证码错误次数过多，请重新获取");
        }
        return MAX_FAIL - used;
    }

    private String normalize(String mobile) {
        String m = mobile == null ? "" : mobile.trim();
        if (!MOBILE_PATTERN.matcher(m).matches()) {
            throw new BizException("手机号格式不正确");
        }
        return m;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String mask(String mobile) {
        if (mobile == null || mobile.length() < 11) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(7);
    }
}
