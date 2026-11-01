package com.xinji.erp.modules.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表服务。所有指标都从业务表实时聚合，
 * 不做预计算也不做缓存 —— 报表最怕的就是和单据页对不上。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ReportMapper reportMapper;

    public List<Map<String, Object>> salesDaily(LocalDate from, LocalDate to) {
        return reportMapper.salesDaily(str(from), str(to));
    }

    public List<Map<String, Object>> customerRanking(LocalDate from, LocalDate to, int limit) {
        return reportMapper.customerRanking(str(from), str(to), limit <= 0 ? 10 : limit);
    }

    public List<Map<String, Object>> skuRanking(LocalDate from, LocalDate to) {
        return reportMapper.skuRanking(str(from), str(to));
    }

    public List<Map<String, Object>> stockFlow(LocalDate from, LocalDate to) {
        return reportMapper.stockFlowSummary(str(from), str(to));
    }

    public List<Map<String, Object>> receivableAging() {
        return reportMapper.receivableAging();
    }

    public List<Map<String, Object>> orderStatus() {
        return reportMapper.orderStatusSummary();
    }

    public List<Map<String, Object>> seckillEffect() {
        return reportMapper.seckillEffect();
    }

    /**
     * 经营总览：一次请求把首页要的指标全带回来，减少前端串行请求。
     * 不传账期默认取本月 1 号到今天。
     */
    public Map<String, Object> overview(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        Map<String, Object> m = new HashMap<>();
        m.put("periodStart", start.toString());
        m.put("periodEnd", end.toString());
        m.put("today", reportMapper.todayMetrics());

        List<Map<String, Object>> daily = reportMapper.salesDaily(str(start), str(end));
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalQty = BigDecimal.ZERO;
        long orderCount = 0;
        for (Map<String, Object> row : daily) {
            totalAmount = totalAmount.add(decimal(row.get("totalAmount")));
            totalQty = totalQty.add(decimal(row.get("totalQty")));
            orderCount += ((Number) row.getOrDefault("orderCount", 0)).longValue();
        }
        m.put("daily", daily);
        m.put("orderCount", orderCount);
        m.put("totalQty", totalQty);
        m.put("totalAmount", totalAmount);
        m.put("avgAmount", orderCount == 0 ? BigDecimal.ZERO
                : totalAmount.divide(BigDecimal.valueOf(orderCount), 2, BigDecimal.ROUND_HALF_UP));
        m.put("customerRanking", reportMapper.customerRanking(str(start), str(end), 10));
        m.put("skuRanking", reportMapper.skuRanking(str(start), str(end)));
        m.put("orderStatus", reportMapper.orderStatusSummary());
        m.put("receivableAging", reportMapper.receivableAging());
        m.put("seckillEffect", reportMapper.seckillEffect());
        return m;
    }

    private String str(LocalDate d) {
        return (d == null ? LocalDate.now() : d).format(DAY);
    }

    private BigDecimal decimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal) {
            return (BigDecimal) v;
        }
        return new BigDecimal(v.toString());
    }
}
