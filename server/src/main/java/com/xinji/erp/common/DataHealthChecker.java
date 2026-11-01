package com.xinji.erp.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 启动数据自检。
 *
 * 存在的意义：只执行了 schema.sql、忘了执行 data.sql 时，系统能正常启动，
 * 但登录会提示「账号或密码错误」、商品列表空白 —— 现象很像代码有 bug，
 * 实际只是没数据。这里在启动后立刻把原因打在控制台，省掉一轮排查。
 */
@Slf4j
@Order(100)
@Component
public class DataHealthChecker implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public DataHealthChecker(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            long users = count("t_user");
            long skus = count("t_sku");
            long orders = count("t_order");
            long customers = count("t_customer");

            if (users == 0 || skus == 0) {
                log.warn("\n"
                        + "========================================================\n"
                        + "  表结构已就绪，但演示数据是空的\n"
                        + "  客户 {} 条 / 账号 {} 条 / 物料 {} 条 / 订单 {} 条\n"
                        + "  这会导致：登录报「账号或密码错误」、商品列表空白\n"
                        + "  解决：执行 server/src/main/resources/db/data.sql\n"
                        + "        mysql -uroot -p < server/src/main/resources/db/data.sql\n"
                        + "========================================================",
                        customers, users, skus, orders);
                return;
            }
            log.info("数据自检通过：客户 {} 家 / 账号 {} 个 / 物料 {} 个 / 订单 {} 张",
                    customers, users, skus, orders);
        } catch (Exception e) {
            log.warn("数据自检跳过：{}（表结构可能还没建，先执行 schema.sql）", e.getMessage());
        }
    }

    private long count(String table) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return n == null ? 0L : n;
    }
}
