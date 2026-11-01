package com.xinji.erp.modules.report;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 报表查询。
 * 全部是只读聚合，直接走 SQL 的 GROUP BY，报表页不缓存，
 * 避免运营看到的数字和订单列表差一截。
 */
@Mapper
public interface ReportMapper {

    /** 按日汇总销售额（取消单不计） */
    @Select("SELECT DATE(o.created_at) AS statDate, COUNT(*) AS orderCount, " +
            "IFNULL(SUM(o.total_qty),0) AS totalQty, IFNULL(SUM(o.pay_amount),0) AS totalAmount " +
            "FROM t_order o WHERE o.status <> 'CANCELLED' " +
            "AND o.created_at >= #{from} AND o.created_at < DATE_ADD(#{to}, INTERVAL 1 DAY) " +
            "GROUP BY DATE(o.created_at) ORDER BY statDate")
    List<Map<String, Object>> salesDaily(@Param("from") String from, @Param("to") String to);

    /** 客户销售排行 */
    @Select("SELECT o.customer_id AS customerId, o.customer_name AS customerName, COUNT(*) AS orderCount, " +
            "IFNULL(SUM(o.total_qty),0) AS totalQty, IFNULL(SUM(o.pay_amount),0) AS totalAmount " +
            "FROM t_order o WHERE o.status <> 'CANCELLED' " +
            "AND o.created_at >= #{from} AND o.created_at < DATE_ADD(#{to}, INTERVAL 1 DAY) " +
            "GROUP BY o.customer_id, o.customer_name ORDER BY totalAmount DESC LIMIT #{limit}")
    List<Map<String, Object>> customerRanking(@Param("from") String from, @Param("to") String to,
                                              @Param("limit") int limit);

    /** 物料销售排行 */
    @Select("SELECT i.sku_id AS skuId, i.sku_name AS skuName, IFNULL(SUM(i.qty),0) AS totalQty, " +
            "IFNULL(SUM(i.amount),0) AS totalAmount " +
            "FROM t_order_item i JOIN t_order o ON o.id = i.order_id " +
            "WHERE o.status <> 'CANCELLED' " +
            "AND o.created_at >= #{from} AND o.created_at < DATE_ADD(#{to}, INTERVAL 1 DAY) " +
            "GROUP BY i.sku_id, i.sku_name ORDER BY totalQty DESC")
    List<Map<String, Object>> skuRanking(@Param("from") String from, @Param("to") String to);

    /** 出入库汇总（按物料 + 业务类型） */
    @Select("SELECT f.sku_id AS skuId, s.sku_name AS skuName, f.biz_type AS bizType, " +
            "IFNULL(SUM(f.qty),0) AS totalQty, COUNT(*) AS flowCount " +
            "FROM t_stock_flow f LEFT JOIN t_sku s ON s.id = f.sku_id " +
            "WHERE f.created_at >= #{from} AND f.created_at < DATE_ADD(#{to}, INTERVAL 1 DAY) " +
            "GROUP BY f.sku_id, s.sku_name, f.biz_type ORDER BY f.sku_id")
    List<Map<String, Object>> stockFlowSummary(@Param("from") String from, @Param("to") String to);

    /** 应收账龄 */
    @Select("SELECT CASE " +
            "WHEN DATEDIFF(NOW(), due_date) <= 0 THEN '未到期' " +
            "WHEN DATEDIFF(NOW(), due_date) <= 30 THEN '逾期1-30天' " +
            "WHEN DATEDIFF(NOW(), due_date) <= 60 THEN '逾期31-60天' " +
            "WHEN DATEDIFF(NOW(), due_date) <= 90 THEN '逾期61-90天' " +
            "ELSE '逾期90天以上' END AS bucket, " +
            "COUNT(*) AS billCount, IFNULL(SUM(amount - received_amount),0) AS outstanding " +
            "FROM t_receivable WHERE status <> 'PAID' " +
            "GROUP BY bucket ORDER BY outstanding DESC")
    List<Map<String, Object>> receivableAging();

    /** 订单状态分布 */
    @Select("SELECT status, COUNT(*) AS cnt, IFNULL(SUM(pay_amount),0) AS amount " +
            "FROM t_order GROUP BY status")
    List<Map<String, Object>> orderStatusSummary();

    /** 秒杀活动效果 */
    @Select("SELECT a.activity_name AS activityName, ss.sku_name AS skuName, " +
            "ss.activity_stock AS activityStock, ss.sold_qty AS soldQty, " +
            "ss.seckill_price AS seckillPrice, " +
            "IFNULL(ss.sold_qty / NULLIF(ss.activity_stock,0), 0) AS sellThroughRate " +
            "FROM t_seckill_sku ss JOIN t_seckill_activity a ON a.id = ss.activity_id " +
            "ORDER BY ss.sold_qty DESC")
    List<Map<String, Object>> seckillEffect();

    /** 今日核心指标 */
    @Select("SELECT (SELECT COUNT(*) FROM t_order WHERE DATE(created_at) = CURDATE()) AS todayOrders, " +
            "(SELECT IFNULL(SUM(pay_amount),0) FROM t_order WHERE DATE(created_at) = CURDATE() " +
            " AND status <> 'CANCELLED') AS todayAmount, " +
            "(SELECT IFNULL(SUM(total_qty),0) FROM t_order WHERE DATE(created_at) = CURDATE() " +
            " AND status <> 'CANCELLED') AS todayQty, " +
            "(SELECT COUNT(*) FROM t_dispatch WHERE status IN ('PENDING','LOADING')) AS dispatching, " +
            "(SELECT COUNT(*) FROM t_production_order WHERE status = 'RUNNING') AS producing")
    Map<String, Object> todayMetrics();
}
