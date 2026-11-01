package com.xinji.erp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 吉林鑫基矿业 · 矿石配送 ERP
 * 模块化单体：一个进程内按业务分包（auth/product/order/stock/seckill/weigh/finance），
 * 对外只暴露两个入口：/api/client/**（客户）与 /api/admin/**（员工）。
 */
@EnableAsync
@EnableScheduling
@MapperScan({
        "com.xinji.erp.modules.auth.mapper",
        "com.xinji.erp.modules.product.mapper",
        "com.xinji.erp.modules.order.mapper",
        "com.xinji.erp.modules.stock.mapper",
        "com.xinji.erp.modules.seckill.mapper",
        "com.xinji.erp.modules.weigh.mapper",
        "com.xinji.erp.modules.finance.mapper",
        "com.xinji.erp.modules.dispatch.mapper",
        "com.xinji.erp.modules.production.mapper",
        "com.xinji.erp.modules.report"
})
@SpringBootApplication
public class ErpApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(ErpApplication.class, args);
        Environment env = ctx.getEnvironment();
        String port = env.getProperty("local.server.port", env.getProperty("server.port", "8080"));
        String base = "http://localhost:" + port;
        System.out.println("\n========================================================");
        System.out.println("  鑫基矿业 ERP 启动完成");
        System.out.println("  服务入口: " + base + "/");
        System.out.println("  接口文档: " + base + "/swagger-ui.html");
        System.out.println("  健康检查: " + base + "/actuator/health");
        System.out.println("========================================================\n");
    }
}
