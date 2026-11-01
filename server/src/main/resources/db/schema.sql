-- =====================================================================
--  吉林鑫基矿业 · 矿石配送 ERP  —— 数据库结构
--  MySQL 8.0 / InnoDB / utf8mb4
--  执行顺序：先 schema.sql，再 data.sql
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `xinji_erp`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE `xinji_erp`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------
-- 1. 客户档案（含授信）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_customer`;
CREATE TABLE `t_customer` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `customer_no`   VARCHAR(32)  NOT NULL COMMENT '客户编号',
  `customer_name` VARCHAR(120) NOT NULL COMMENT '客户名称',
  `contact_name`  VARCHAR(40)           DEFAULT NULL COMMENT '联系人',
  `contact_phone` VARCHAR(20)           DEFAULT NULL COMMENT '联系电话',
  `address`       VARCHAR(200)          DEFAULT NULL COMMENT '收货地址',
  `credit_limit`  DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '授信额度（元）',
  `credit_used`   DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '已用额度（元）',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0停用',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_no` (`customer_no`),
  KEY `idx_contact_phone` (`contact_phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户档案';

-- ---------------------------------------------------------------------
-- 2. 登录账号（客户账号 + 员工账号统一表，用 user_type 区分）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `username`      VARCHAR(40) NOT NULL COMMENT '登录名',
  `password`      VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码',
  `mobile`        VARCHAR(20)          DEFAULT NULL COMMENT '手机号',
  `user_type`     VARCHAR(16) NOT NULL COMMENT 'CUSTOMER 客户 / EMPLOYEE 员工',
  `customer_id`   BIGINT               DEFAULT NULL COMMENT '客户账号关联的客户ID',
  `employee_name` VARCHAR(40)          DEFAULT NULL COMMENT '员工姓名',
  `role`          VARCHAR(24) NOT NULL DEFAULT 'CUSTOMER' COMMENT 'ADMIN/SALES/FINANCE/WEIGH/DISPATCH/CUSTOMER',
  `status`        TINYINT     NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_mobile` (`mobile`),
  KEY `idx_customer` (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录账号';

-- ---------------------------------------------------------------------
-- 3. 料仓
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_warehouse`;
CREATE TABLE `t_warehouse` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `warehouse_no`   VARCHAR(20)  NOT NULL COMMENT '料仓编号 A1/A2/B1/B2',
  `warehouse_name` VARCHAR(60)  NOT NULL COMMENT '料仓名称',
  `capacity`       DECIMAL(14,3) NOT NULL DEFAULT 0 COMMENT '设计容量（吨）',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wh_no` (`warehouse_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='料仓';

-- ---------------------------------------------------------------------
-- 4. 物料 SKU —— 库存双池的核心表
--    stock_qty 是「销售可售池」，客户下单只扣这个字段
--    activity_alloc_qty 是「活动池划拨量」，划拨后从 stock_qty 减出
--    frozen_qty 是「订单冻结」，审核通过后不动，发货时核销
--    物理总库存 = stock_qty + activity_alloc_qty + frozen_qty
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_sku`;
CREATE TABLE `t_sku` (
  `id`                BIGINT        NOT NULL AUTO_INCREMENT,
  `sku_no`            VARCHAR(32)   NOT NULL COMMENT '物料编码',
  `sku_name`          VARCHAR(80)   NOT NULL COMMENT '物料名称',
  `category`          VARCHAR(30)   NOT NULL COMMENT '品种：石灰石/机制砂/铁精粉/石粉/尾矿',
  `spec`              VARCHAR(60)            DEFAULT NULL COMMENT '规格',
  `unit`              VARCHAR(10)   NOT NULL DEFAULT '吨',
  `price`             DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '挂牌价（元/吨）',
  `warehouse_id`      BIGINT                 DEFAULT NULL COMMENT '主料仓',
  `stock_qty`         DECIMAL(14,3) NOT NULL DEFAULT 0 COMMENT '销售可售量',
  `activity_alloc_qty` DECIMAL(14,3) NOT NULL DEFAULT 0 COMMENT '活动池划拨量',
  `frozen_qty`        DECIMAL(14,3) NOT NULL DEFAULT 0 COMMENT '订单冻结量',
  `safety_qty`        DECIMAL(14,3) NOT NULL DEFAULT 0 COMMENT '安全库存线',
  `image_tag`         VARCHAR(20)            DEFAULT NULL COMMENT '前端占位色标',
  `status`            TINYINT       NOT NULL DEFAULT 1 COMMENT '1在售 0下架',
  `created_at`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_no` (`sku_no`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料SKU（含库存双池）';

-- ---------------------------------------------------------------------
-- 5. 订单
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_order`;
CREATE TABLE `t_order` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT,
  `order_no`        VARCHAR(32)   NOT NULL COMMENT '订单号',
  `customer_id`     BIGINT        NOT NULL COMMENT '客户ID',
  `customer_name`   VARCHAR(120)  NOT NULL COMMENT '客户名称冗余',
  `order_type`      VARCHAR(16)   NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL 常规 / SECKILL 抢购',
  `status`          VARCHAR(24)   NOT NULL COMMENT '订单状态',
  `total_qty`       DECIMAL(14,3) NOT NULL DEFAULT 0 COMMENT '下单吨数',
  `total_amount`    DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '商品金额',
  `freight`         DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '运费',
  `discount_amount` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '优惠',
  `pay_amount`      DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '应付金额',
  `delivery_type`   VARCHAR(16)   NOT NULL DEFAULT 'DELIVERY' COMMENT 'DELIVERY 配送 / PICKUP 自提',
  `expected_date`   VARCHAR(40)            DEFAULT NULL COMMENT '期望到货时段',
  `address`         VARCHAR(200)           DEFAULT NULL COMMENT '卸货地址',
  `receiver`        VARCHAR(80)            DEFAULT NULL COMMENT '收货人',
  `seckill_token`   VARCHAR(40)            DEFAULT NULL COMMENT '抢购令牌号',
  `remark`          VARCHAR(200)           DEFAULT NULL,
  `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_customer` (`customer_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单';

-- ---------------------------------------------------------------------
-- 6. 订单明细
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_order_item`;
CREATE TABLE `t_order_item` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT,
  `order_id`     BIGINT        NOT NULL,
  `order_no`     VARCHAR(32)   NOT NULL,
  `sku_id`       BIGINT        NOT NULL,
  `sku_name`     VARCHAR(80)   NOT NULL,
  `spec`         VARCHAR(60)            DEFAULT NULL,
  `qty`          DECIMAL(14,3) NOT NULL COMMENT '下单吨数',
  `price`        DECIMAL(12,2) NOT NULL,
  `amount`       DECIMAL(14,2) NOT NULL,
  `settle_qty`   DECIMAL(14,3) NOT NULL DEFAULT 0 COMMENT '结算吨数（按磅单净重扣水杂）',
  `settle_amount` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '结算金额',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细';

-- ---------------------------------------------------------------------
-- 7. 库存流水 —— 每一次库存变动都必须有据可查
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_stock_flow`;
CREATE TABLE `t_stock_flow` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT,
  `flow_no`      VARCHAR(32)   NOT NULL COMMENT '流水号',
  `sku_id`       BIGINT        NOT NULL,
  `warehouse_id` BIGINT                 DEFAULT NULL,
  `biz_type`     VARCHAR(20)   NOT NULL COMMENT 'INBOUND入库/OUTBOUND出库/FREEZE冻结/UNFREEZE解冻/ALLOC划拨/RETURN回拨/CHECK盘点',
  `qty`          DECIMAL(14,3) NOT NULL COMMENT '变动量（正负）',
  `before_qty`   DECIMAL(14,3) NOT NULL COMMENT '变动前可售量',
  `after_qty`    DECIMAL(14,3) NOT NULL COMMENT '变动后可售量',
  `biz_no`       VARCHAR(32)            DEFAULT NULL COMMENT '关联单号',
  `remark`       VARCHAR(200)           DEFAULT NULL,
  `operator`     VARCHAR(40)            DEFAULT NULL,
  `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sku_time` (`sku_id`,`created_at`),
  KEY `idx_biz_no` (`biz_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存流水';

-- ---------------------------------------------------------------------
-- 8. 地磅单
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_weigh_ticket`;
CREATE TABLE `t_weigh_ticket` (
  `id`                   BIGINT        NOT NULL AUTO_INCREMENT,
  `ticket_no`            VARCHAR(32)   NOT NULL COMMENT '磅单号',
  `order_no`             VARCHAR(32)   NOT NULL,
  `sku_id`               BIGINT        NOT NULL,
  `plate_no`             VARCHAR(20)   NOT NULL COMMENT '车牌',
  `driver_name`          VARCHAR(40)            DEFAULT NULL,
  `driver_phone`         VARCHAR(20)            DEFAULT NULL,
  `gross_weight`         DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '毛重',
  `tare_weight`          DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '皮重',
  `net_weight`           DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '净重 = 毛重 - 皮重',
  `water_rate`           DECIMAL(6,4)  NOT NULL DEFAULT 0 COMMENT '含水率',
  `water_deduct_weight`  DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '扣水量',
  `impurity_deduct_weight` DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '扣杂量',
  `settle_weight`        DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '结算吨数',
  `weigh_time`           DATETIME               DEFAULT NULL,
  `weighman`             VARCHAR(40)            DEFAULT NULL,
  `status`               VARCHAR(16)   NOT NULL DEFAULT 'ISSUED' COMMENT 'ISSUED已出票/VOID作废',
  `created_at`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ticket_no` (`ticket_no`),
  KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地磅单';

-- ---------------------------------------------------------------------
-- 9. 秒杀活动
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_seckill_activity`;
CREATE TABLE `t_seckill_activity` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `activity_no`   VARCHAR(32)  NOT NULL COMMENT '活动编号',
  `activity_name` VARCHAR(120) NOT NULL,
  `start_time`    DATETIME     NOT NULL,
  `end_time`      DATETIME     NOT NULL,
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/RUNNING/ENDED',
  `warmup_status` VARCHAR(16)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PENDING/DONE',
  `created_by`    VARCHAR(40)           DEFAULT NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_no` (`activity_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动';

-- ---------------------------------------------------------------------
-- 10. 活动 SKU —— 活动库存池，与 t_sku.stock_qty 物理分离
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_seckill_sku`;
CREATE TABLE `t_seckill_sku` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT,
  `activity_id`    BIGINT        NOT NULL,
  `seckill_sku_no` VARCHAR(32)   NOT NULL COMMENT '活动SKU编号 ASK-xxxxx',
  `sku_id`         BIGINT        NOT NULL,
  `sku_name`       VARCHAR(80)   NOT NULL,
  `seckill_price`  DECIMAL(12,2) NOT NULL COMMENT '抢购价',
  `original_price` DECIMAL(12,2) NOT NULL COMMENT '原价',
  `activity_stock` DECIMAL(14,3) NOT NULL COMMENT '活动库存（划拨而来的独立池）',
  `sold_qty`       DECIMAL(14,3) NOT NULL DEFAULT 0 COMMENT '已抢吨数',
  `limit_per_user` DECIMAL(14,3) NOT NULL DEFAULT 60 COMMENT '每人限购吨数',
  `status`         VARCHAR(16)   NOT NULL DEFAULT 'ON' COMMENT 'ON/OFF/SOLDOUT',
  `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ask_no` (`seckill_sku_no`),
  UNIQUE KEY `uk_activity_sku` (`activity_id`,`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动SKU（活动库存池）';

-- ---------------------------------------------------------------------
-- 11. 抢购令牌 —— 抢到的是「提货凭证」，不是订单
--     uk_user_sku 是「一人一单」的数据库兜底
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_seckill_token`;
CREATE TABLE `t_seckill_token` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT,
  `token_no`        VARCHAR(40)   NOT NULL COMMENT '令牌号',
  `user_id`         BIGINT        NOT NULL,
  `customer_id`     BIGINT                 DEFAULT NULL,
  `activity_id`     BIGINT        NOT NULL,
  `seckill_sku_id`  BIGINT        NOT NULL,
  `sku_id`          BIGINT        NOT NULL,
  `qty`             DECIMAL(14,3) NOT NULL,
  `price`           DECIMAL(12,2) NOT NULL,
  `status`          VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING发放中/ISSUED待核销/USED已下单/EXPIRED已过期/FAILED失败',
  `expire_time`     DATETIME               DEFAULT NULL,
  `order_no`        VARCHAR(32)            DEFAULT NULL,
  `fail_reason`     VARCHAR(200)           DEFAULT NULL,
  `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token_no` (`token_no`),
  UNIQUE KEY `uk_user_sku` (`user_id`,`seckill_sku_id`),
  KEY `idx_activity` (`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抢购令牌';

-- ---------------------------------------------------------------------
-- 12. 对账批次
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_settlement`;
CREATE TABLE `t_settlement` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT,
  `settlement_no`  VARCHAR(32)   NOT NULL,
  `customer_id`    BIGINT        NOT NULL,
  `customer_name`  VARCHAR(120)  NOT NULL,
  `period_start`   DATE          NOT NULL,
  `period_end`     DATE          NOT NULL,
  `order_count`    INT           NOT NULL DEFAULT 0,
  `total_qty`      DECIMAL(14,3) NOT NULL DEFAULT 0,
  `total_amount`   DECIMAL(14,2) NOT NULL DEFAULT 0,
  `status`         VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/CONFIRMED/INVOICED',
  `confirmed_at`   DATETIME               DEFAULT NULL,
  `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_no` (`settlement_no`),
  KEY `idx_customer` (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对账批次';

-- ---------------------------------------------------------------------
-- 13. 对账明细
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_settlement_item`;
CREATE TABLE `t_settlement_item` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT,
  `settlement_id` BIGINT        NOT NULL,
  `order_id`      BIGINT        NOT NULL,
  `order_no`      VARCHAR(32)   NOT NULL,
  `settle_qty`    DECIMAL(14,3) NOT NULL,
  `amount`        DECIMAL(14,2) NOT NULL,
  `settle_date`   DATE          NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_settlement` (`settlement_id`),
  UNIQUE KEY `uk_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对账明细';

-- ---------------------------------------------------------------------
-- 14. 应收账款
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_receivable`;
CREATE TABLE `t_receivable` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT,
  `receivable_no`   VARCHAR(32)   NOT NULL,
  `customer_id`     BIGINT        NOT NULL,
  `customer_name`   VARCHAR(120)  NOT NULL,
  `settlement_id`   BIGINT                 DEFAULT NULL,
  `amount`          DECIMAL(14,2) NOT NULL,
  `received_amount` DECIMAL(14,2) NOT NULL DEFAULT 0,
  `status`          VARCHAR(16)   NOT NULL DEFAULT 'UNPAID' COMMENT 'UNPAID/PARTIAL/PAID',
  `due_date`        DATE                   DEFAULT NULL,
  `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_receivable_no` (`receivable_no`),
  KEY `idx_customer` (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应收账款';

-- ---------------------------------------------------------------------
-- 15. 采购单（MVP：只做到货入库单向）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_purchase_order`;
CREATE TABLE `t_purchase_order` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT,
  `po_no`         VARCHAR(32)   NOT NULL,
  `supplier_name` VARCHAR(120)  NOT NULL,
  `sku_id`        BIGINT        NOT NULL,
  `sku_name`      VARCHAR(80)   NOT NULL,
  `plan_qty`      DECIMAL(14,3) NOT NULL,
  `arrived_qty`   DECIMAL(14,3) NOT NULL DEFAULT 0,
  `price`         DECIMAL(12,2) NOT NULL,
  `arrived_amount` DECIMAL(14,2) NOT NULL DEFAULT 0,
  `status`        VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING在途/PARTIAL部分到货/DONE已完成',
  `expected_date` DATE                   DEFAULT NULL,
  `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_po_no` (`po_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购单';

-- ---------------------------------------------------------------------
-- 16. 车辆档案
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_vehicle`;
CREATE TABLE `t_vehicle` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT,
  `plate_no`         VARCHAR(20)   NOT NULL COMMENT '车牌号',
  `vehicle_type`     VARCHAR(30)   NOT NULL COMMENT '车型：自卸车/半挂/罐车',
  `load_capacity`    DECIMAL(10,3) NOT NULL DEFAULT 0 COMMENT '核定载重（吨）',
  `owner_name`       VARCHAR(60)            DEFAULT NULL COMMENT '车主',
  `owner_phone`      VARCHAR(20)            DEFAULT NULL COMMENT '车主电话',
  `insurance_expire` DATE                   DEFAULT NULL COMMENT '保险到期日',
  `status`           TINYINT       NOT NULL DEFAULT 1 COMMENT '1可用 0停用',
  `created_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plate_no` (`plate_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车辆档案';

-- ---------------------------------------------------------------------
-- 17. 司机档案
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_driver`;
CREATE TABLE `t_driver` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `driver_name` VARCHAR(40) NOT NULL COMMENT '司机姓名',
  `phone`       VARCHAR(20) NOT NULL COMMENT '联系电话',
  `id_card`     VARCHAR(24)          DEFAULT NULL COMMENT '身份证号（脱敏存储）',
  `license_no`  VARCHAR(32)          DEFAULT NULL COMMENT '驾驶证号',
  `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '1可用 0停用',
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='司机档案';

-- ---------------------------------------------------------------------
-- 18. 派车单 —— 订单审核通过后派车，与过磅单、订单用 order_no 串起来
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_dispatch`;
CREATE TABLE `t_dispatch` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT,
  `dispatch_no`  VARCHAR(32)   NOT NULL COMMENT '派车单号',
  `order_no`     VARCHAR(32)   NOT NULL COMMENT '关联订单号',
  `customer_name` VARCHAR(120) NOT NULL,
  `vehicle_id`   BIGINT                 DEFAULT NULL,
  `plate_no`     VARCHAR(20)   NOT NULL COMMENT '车牌号冗余',
  `driver_id`    BIGINT                 DEFAULT NULL,
  `driver_name`  VARCHAR(40)   NOT NULL,
  `driver_phone` VARCHAR(20)            DEFAULT NULL,
  `plan_qty`     DECIMAL(14,3) NOT NULL DEFAULT 0 COMMENT '计划装载吨数',
  `plan_time`    DATETIME               DEFAULT NULL COMMENT '计划发车时间',
  `status`       VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待发车/LOADING装车中/SHIPPED已出厂/DONE已签收/CANCELLED已取消',
  `remark`       VARCHAR(200)           DEFAULT NULL,
  `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dispatch_no` (`dispatch_no`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派车单';

-- ---------------------------------------------------------------------
-- 19. 生产工单 —— 破碎线加工，完工后成品入销售可售池
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_production_order`;
CREATE TABLE `t_production_order` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT,
  `production_no`  VARCHAR(32)   NOT NULL COMMENT '生产工单号',
  `line_name`      VARCHAR(40)   NOT NULL COMMENT '产线：一号破碎线/二号制砂线',
  `shift`          VARCHAR(16)   NOT NULL COMMENT '班次：白班/中班/夜班',
  `sku_id`         BIGINT        NOT NULL COMMENT '产出物料',
  `sku_name`       VARCHAR(80)   NOT NULL,
  `plan_qty`       DECIMAL(14,3) NOT NULL COMMENT '计划产量（吨）',
  `output_qty`     DECIMAL(14,3) NOT NULL DEFAULT 0 COMMENT '实际产量（吨）',
  `feed_qty`       DECIMAL(14,3) NOT NULL DEFAULT 0 COMMENT '投料量（吨）',
  `status`         VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待开始/RUNNING生产中/DONE已完工/CANCELLED已取消',
  `start_time`     DATETIME               DEFAULT NULL,
  `end_time`       DATETIME               DEFAULT NULL,
  `operator`       VARCHAR(40)            DEFAULT NULL COMMENT '班组长',
  `remark`         VARCHAR(200)           DEFAULT NULL,
  `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_production_no` (`production_no`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产工单';

-- ---------------------------------------------------------------------
-- 20. 收款流水 —— 应收单的每一次收款都要留痕，不能只累加一个数字
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_payment`;
CREATE TABLE `t_payment` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT,
  `payment_no`     VARCHAR(32)   NOT NULL COMMENT '收款流水号',
  `receivable_id`  BIGINT        NOT NULL,
  `receivable_no`  VARCHAR(32)   NOT NULL,
  `customer_id`    BIGINT        NOT NULL,
  `customer_name`  VARCHAR(120)  NOT NULL,
  `amount`         DECIMAL(14,2) NOT NULL COMMENT '本次收款金额',
  `pay_method`     VARCHAR(16)   NOT NULL DEFAULT 'TRANSFER' COMMENT 'TRANSFER转账/CASH现金/CHECK承兑/DRAFT汇票',
  `pay_time`       DATETIME      NOT NULL COMMENT '收款时间',
  `operator`       VARCHAR(40)            DEFAULT NULL,
  `remark`         VARCHAR(200)           DEFAULT NULL,
  `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_no` (`payment_no`),
  KEY `idx_receivable` (`receivable_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收款流水';

-- ---------------------------------------------------------------------
-- 21. 发票
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `t_invoice`;
CREATE TABLE `t_invoice` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT,
  `invoice_no`     VARCHAR(32)   NOT NULL COMMENT '发票申请号',
  `settlement_id`  BIGINT                 DEFAULT NULL COMMENT '关联对账批次',
  `receivable_id`  BIGINT                 DEFAULT NULL COMMENT '关联应收单',
  `customer_id`    BIGINT        NOT NULL,
  `customer_name`  VARCHAR(120)  NOT NULL,
  `invoice_type`   VARCHAR(16)   NOT NULL DEFAULT 'VAT_SPECIAL' COMMENT 'VAT_SPECIAL专票/VAT_NORMAL普票',
  `amount`         DECIMAL(14,2) NOT NULL COMMENT '价税合计',
  `tax_rate`       DECIMAL(6,4)  NOT NULL DEFAULT 0.1300 COMMENT '税率',
  `tax_amount`     DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '税额',
  `status`         VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待开具/ISSUED已开具/VOID已作废',
  `issued_at`      DATETIME               DEFAULT NULL,
  `remark`         VARCHAR(200)           DEFAULT NULL,
  `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invoice_no` (`invoice_no`),
  KEY `idx_customer` (`customer_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发票';

SET FOREIGN_KEY_CHECKS = 1;
