# 吉林鑫基矿业 · 矿石配送 ERP

> 矿石配送全链路系统：客户在**网页商城**和**微信小程序**下单，矿区在**运营后台**完成审核、派车、过磅、出库、对账、收款。
> 解决前端下单与后端仓储、采购、财务数据割裂的问题。

---

## 1. 这套系统解决什么问题

改造前，鑫基矿业的业务分散在微信、纸质磅单、Excel 台账里：

| 环节 | 改造前 | 改造后 |
| --- | --- | --- |
| 客户下单 | 微信语音 / 手写单，经常报错吨数 | 网页商城 + 小程序自助下单，库存实时校验 |
| 库存 | 销售和活动各记一本账，经常对不上 | 库存双池模型，任何变动都写台账，可逐笔倒推 |
| 过磅 | 纸质磅单，扣水扣杂靠手算 | 过磅自动算净重/扣水/扣杂/结算量，直接回填订单 |
| 派车 | 调度打电话找车，车牌和订单对不上 | 车辆/司机档案 + 派车单，超载、保险过期、重复派车都会被拦 |
| 生产 | 产量记在班组本子上 | 生产工单完工报产直接入销售可售池，和采购入库共用一套台账 |
| 对账 | 月底拿 Excel 拼，来回扯皮 | 按账期一键生成对账批次，客户确认后自动生成应收、开票、回款流水 |
| 促销 | 只能人工口头给优惠 | 秒杀活动独立活动库存池，Redis Lua 原子扣减防超卖 |

## 2. 三个端，物理分开

| 端 | 页面文件 | 使用者 | 接口前缀 | 鉴权 |
| --- | --- | --- | --- | --- |
| 网页商城 | `prototype/mall.html` | 客户 | `/api/client/**` | 客户账号 JWT |
| 微信小程序 | `prototype/miniapp.html` | 客户 | `/api/client/**` | 同一套 `customer_id` |
| 运营后台 | `prototype/admin.html` | 矿区员工 | `/api/admin/**` | 员工账号 JWT + 角色 |

**关键点**：网页商城和小程序是同一个客户身份（绑定同一个 `customer_id`），在商城加的购物车、下的单，小程序登录后能看到；但运营后台客户端**完全访问不到** —— 不是靠前端藏菜单，而是后端 `AuthInterceptor` 直接拦掉 `/api/admin/**`，客户 token 访问返回 `403 无权限访问管理后台接口`。

## 3. 技术栈

- Java 8 / Spring Boot 2.7.18
- MyBatis-Plus 3.5.3.1 / MySQL 8.0
- Redis（Lua 脚本、Stream 消息队列）、Redisson 分布式锁
- Caffeine 本地缓存 + Memcached（可选）+ Redis 三级缓存
- JWT（jjwt 0.11.5）、BCrypt 密码加密
- springdoc-openapi（Swagger UI）

## 4. 5 分钟跑起来

### 4.1 在 IDEA 里跑（推荐）

1. **解压**压缩包到任意目录（路径别带中文和空格）。
2. IDEA → `File` → `Open` → 选中**解压出来的根目录**（里面有根 `pom.xml` 的那一层），点 `OK` → `Trust Project`。
   IDEA 会自动把 `server` 识别成子模块，等右下角 Maven 依赖下载完（首次约 2~5 分钟）。
3. 确认 `File` → `Project Structure` → `Project SDK` 选的是 **JDK 1.8**，`Language level` 选 `8`。
4. 建库（在 Navicat / 命令行都行，**先建库再灌数据，顺序不能反**）：
   ```bash
   mysql -uroot -p < server/src/main/resources/db/schema.sql   # 21 张表
   mysql -uroot -p < server/src/main/resources/db/data.sql     # 演示数据
   ```
5. 改数据库密码：打开 `server/src/main/resources/application.yml`，把 `dev` 段里的
   `username: root` / `password: 1234` 改成你本机的。
6. 确认本机 Redis 已启动（默认连 `127.0.0.1:6379` 的 **5 号库**，不冲突你别的项目）：
   ```bash
   redis-cli ping   # 返回 PONG 即可
   ```
7. 运行 `com.xinji.erp.ErpApplication` 的 `main` 方法。控制台出现 `Started ErpApplication` 就是起来了。
8. **控制台中文乱码**（GBK 环境常见）：Run/Debug Configurations → 你的配置 → `VM options` 填 `-Dfile.encoding=UTF-8`，再在 `Settings → Editor → File Encodings` 把三处都设成 UTF-8，重启即可。乱码只影响日志显示，接口返回的 JSON 一直是 UTF-8。

> 首次启动会自动建 Redis 消费者组、预热数据；如果 Redis 里之前有别的项目数据，不用清，本工程只用 5 号库。

### 4.2 命令行跑

```bash
cd xinji-erp
mvn clean package -DskipTests
java -Dfile.encoding=UTF-8 -jar server/target/xinji-erp.jar
```

### 4.3 启动后从哪里看

**浏览器打开 <http://localhost:8080/> 会看到一个服务入口页**，上面有接口文档链接、数据库/Redis 实时状态、演示账号。

| 地址 | 是什么 |
| --- | --- |
| <http://localhost:8080/> | 服务入口页（导航 + 环境状态） |
| <http://localhost:8080/swagger-ui.html> | 接口文档，113 个接口可直接调试 |
| <http://localhost:8080/actuator/health> | 健康检查，MySQL/Redis 通不通看这里 |
| `prototype/mall.html` | 网页商城原型（本地文件，双击打开） |
| `prototype/admin.html` | 运营后台原型（本地文件，双击打开） |

> **注意**：直接访问 `/api/client/products` 会返回 401，这是鉴权生效的正常表现。先在 Swagger 里调 `/api/auth/login` 拿 token，右上角 Authorize 填进去再调业务接口。
>
> 根路径早期版本没有页面（返回 Whitelabel 404），现在已内置入口页；如果你看到的是白页，说明跑的是旧包，重新打包或重新构建一次即可。

**启动时会自动做数据自检**：如果只执行了 `schema.sql`、漏了 `data.sql`，控制台会直接告警「演示数据是空的」，而不是让你对着「账号或密码错误」猜半天。

**默认账号**

| 账号 | 密码 | 身份 | 说明 |
| --- | --- | --- | --- |
| `admin` | `admin123` | 管理员 | 全部后台权限 |
| `sales01` | `123456` | 销售 | 订单审核、派车 |
| `finance01` | `123456` | 财务 | 对账、收款 |
| `weigh01` | `123456` | 磅房 | 过磅出票 |
| `yatai` | `123456` | 客户 | 长春亚泰建材 |
| `jianlong` | `123456` | 客户 | 吉林建龙钢铁 |
| `yatai_sn` | `123456` | 客户 | 吉林亚泰水泥 |

## 5. 目录结构

```
xinji-erp/
├── pom.xml                    # 根聚合 pom（IDEA 打开这一层就能导入）
├── prototype/                 # 前端高保真原型（三端 + 架构说明，纯静态可直接打开）
│   ├── index.html             # 原型索引：三端隔离对比、双池模型说明
│   ├── mall.html              # 网页商城
│   ├── miniapp.html           # 微信小程序（手机壳）
│   ├── admin.html             # 运营后台（带登录门禁）
│   ├── arch.html              # 技术架构与秒杀时序
│   └── assets/ui.css          # 共用设计层
├── server/                    # 后端工程（模块化单体）
│   ├── pom.xml
│   ├── scripts/acceptance.sh  # 端到端验收脚本
│   └── src/main/
│       ├── java/com/xinji/erp/
│       │   ├── common/        # 统一返回、异常、鉴权、多级缓存、单号生成
│       │   ├── config/        # MyBatis-Plus、Redis、缓存、拦截器、Jackson、OpenAPI
│       │   └── modules/       # 按业务域分包：
│       │                      #   auth(登录/账号) customer(客户授信) product(物料档案)
│       │                      #   order(订单) stock(库存) seckill(秒杀) weigh(过磅)
│       │                      #   dispatch(车辆/司机/派车) production(生产)
│       │                      #   finance(采购/对账/应收/收款/发票) report(报表)
│       └── resources/
│           ├── application.yml
│           ├── static/index.html  # 服务入口页（启动后访问 / 就能看到）
│           ├── db/schema.sql  # 21 张表
│           ├── db/data.sql    # 演示数据
│           └── lua/seckill.lua# 秒杀原子脚本
├── docs/                      # 部署文档、接口文档
└── docker-compose.yml         # 本地依赖（MySQL + Redis，可选 Memcached）
```

## 6. 文档索引

| 文档 | 内容 |
| --- | --- |
| [docs/部署文档.md](docs/部署文档.md) | 环境准备、建库、配置项说明、启动方式、验收步骤、上线检查、FAQ |
| [docs/接口清单.md](docs/接口清单.md) | 全部接口按模块列出，含请求参数与返回示例 |
| [docs/数据库设计.md](docs/数据库设计.md) | 21 张表的字段说明与设计取舍 |
| [docs/api.http](docs/api.http) | 在 IDEA 里直接点绿色箭头运行的请求集 |

## 6.1 功能清单

| 模块 | 能力 |
| --- | --- |
| 客户端 | 商品列表（多级缓存）、下单冻结、我的订单、取消、秒杀抢购、令牌兑换 |
| 订单中心 | 分页/详情、审核、派车、过磅出厂、签收、取消、状态机 |
| 仓储管理 | 库存总览、流水台账、活动划拨 / 回拨、采购入库、库存预警 |
| 物料档案 | 物料增改、上下架、期初入库、料仓维护 |
| 采购管理 | 建单、到货入库、详情、取消 |
| 调度过磅 | 车辆 / 司机档案、证照到期提醒、派车、装车、过磅出票 |
| 生产管理 | 工单建档、开工、完工报产入库、成品率看板 |
| 客户与授信 | 客户建档、授信调整、额度预警、开客户账号 |
| 财务结算 | 对账批次、应收、收款流水、发票开具/作废、回款统计 |
| 秒杀活动 | 活动建档、发布、SKU 配置（活动价/限购）、库存划拨、预热、结束回拨、令牌监控 |
| 报表中心 | 经营总览、销售日报、客户/物料排行、出入库汇总、应收账龄、秒杀效果 |
| 系统设置 | 员工账号、角色、客户账号绑定、重置密码、停用 |

## 7. 核心设计说明

### 库存双池

一张 `t_sku` 上有三个数：

```
physical(物理总库存) = stock_qty(销售可售) + activity_alloc_qty(活动划拨) + frozen_qty(订单冻结)
```

- 下单 → `stock_qty↓` `frozen_qty↑`，物理总量不变（货还在矿区，只是被锁住）
- 出厂 → `frozen_qty↓`，物理总量减少（货真走了）
- 划拨活动 → `stock_qty↓` `activity_alloc_qty↑`，物理总量不变
- 秒杀成交 → `activity_alloc_qty↓`，物理总量减少

所以「活动库存独立于销售库存」不是新开一张表，而是在同一个物料上切出两个池子，任何一笔变动都写 `t_stock_flow`，随时能倒推。

### 秒杀流程

```
预热（DB→Redis 库存 + 已抢用户集合）
  → 抢购：Lua 脚本原子完成「一人一单校验 + 库存判断 + 扣减 + 记已抢 + XADD 发消息」
  → 异步发放：Redis Stream 消费组落库发令牌，手动 ACK，pending-list 定时补偿
  → 令牌兑换：客户拿令牌下单，活动价成交，令牌置 USED
  → 超时释放：15 分钟未下单，令牌作废 + 活动库存归还 Redis 与 DB
```

三重幂等：Redisson 用户锁 → `(user_id, seckill_sku_id)` 唯一索引 → 条件扣减 `activity_stock - sold_qty >= qty`。
