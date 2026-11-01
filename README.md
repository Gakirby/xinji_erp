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

三个端是**三套独立的前端工程**，共用同一个后端，靠 nginx 路径隔离：

| 端 | 前端工程 | 线上路径 | 构建产物 | 使用者 | 接口前缀 |
| --- | --- | --- | --- | --- | --- |
| 网页商城 | `web/mall` | `/` | `deploy/html/index.html` | 客户 | `/api/client/**` |
| 运营后台 | `web/admin` | `/admin/` | `deploy/html/admin/` | 矿区员工 | `/api/admin/**` |
| 手机端 H5 | `web/mobile` | `/m/` | `deploy/html/m/` | 客户（手机浏览器 / 小程序 H5） | `/api/client/**` |

**关键点**：网页商城和手机端是同一个客户身份（绑定同一个 `customer_id`），在商城下的单，手机端登录后能看到；但运营后台客户端**完全访问不到** —— 不是靠前端藏菜单，而是后端 `AuthInterceptor` 直接拦掉 `/api/admin/**`，客户 token 访问返回 `403 无权限访问管理后台接口`。

三个端同域名共存，token 因此按端分开存（`xj_client_token` / `xj_admin_token`），避免在 `/` 登录客户账号把 `/admin/` 的员工登录态顶掉。

## 3. 技术栈

**后端**

- Java 8 / Spring Boot 2.7.18
- MyBatis-Plus 3.5.3.1 / MySQL 8.0
- Redis（Lua 脚本、Stream 消息队列）、Redisson 分布式锁
- Caffeine 本地缓存 + Memcached（可选）+ Redis 三级缓存
- JWT（jjwt 0.11.5）、BCrypt 密码加密
- springdoc-openapi（Swagger UI）

**前端**

- Vue 3.5 + Vite 5.4 + vue-router 4（history 模式）
- axios 1.7（统一拦截器：剥 `{code,msg,data}`、401 跳登录）
- Element Plus 2.9（仅运营后台，中文 locale）
- npm workspaces：`shared` + `mall` + `admin` + `mobile` 四个包，共用接口层

**部署**

- Nginx 1.27（三端路径隔离 + `/api` 反代 + gzip + 长缓存）
- Docker 多阶段构建（node 构建前端 → nginx 托管；maven 构建后端 → JRE 运行）

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
| <http://localhost:8080/> | 服务入口页（导航 + 环境状态 + 三端入口说明） |
| <http://localhost:8080/swagger-ui.html> | 接口文档，116 个接口可直接调试 |
| <http://localhost:8080/actuator/health> | 健康检查，MySQL/Redis 通不通看这里 |

> **注意**：直接访问 `/api/client/products` 会返回 401，这是鉴权生效的正常表现。先在 Swagger 里调 `/api/auth/login` 拿 token，右上角 Authorize 填进去再调业务接口。
>
> 根路径早期版本没有页面（返回 Whitelabel 404），现在已内置入口页；如果你看到的是白页，说明跑的是旧包，重新打包或重新构建一次即可。

**启动时会自动做数据自检**：如果只执行了 `schema.sql`、漏了 `data.sql`，控制台会直接告警「演示数据是空的」，而不是让你对着「账号或密码错误」猜半天。

### 4.4 前端三端页面怎么跑起来

后端只是 API，**页面由前端工程提供**。两种方式，任选一种：

**方式 A：本地预览（不装 nginx / Docker，最快）**

```bash
cd xinji-erp/web
npm install                 # 首次约 1 分钟
npm run build               # 三端一次构建，产物落到 deploy/html

cd ..
node deploy/preview.mjs     # 默认 8090，代理 /api 到 8080
```

然后打开：

| 地址 | 端 |
| --- | --- |
| <http://localhost:8090/> | 网页商城 |
| <http://localhost:8090/admin/> | 运营后台（员工账号登录） |
| <http://localhost:8090/m/> | 手机端（建议用浏览器 F12 切手机视图） |

`preview.mjs` 是零依赖的 Node 脚本（不需要 npm install），它按 `deploy/nginx.conf` 的同一套规则做 history 路由回退和 `/api` 反向代理，**在没有 nginx 的机器上的兜底方案**。日常推荐用下面的方式 C（真 nginx）。
后端地址不用手动指定：它按 `8081 → 8080 → 8082` 探测谁活着（`start-backend.bat` 起的 jar 在 8081，IDEA 里跑的在 8080），探测不到会明确提示「后端没起来」。

**方式 B：整站 Docker（含 nginx + MySQL + Redis）**

```bash
cd xinji-erp/deploy
docker compose up -d --build
```

启动后 nginx 监听 `80`，直接访问 <http://localhost/> 即可（需要 Docker Desktop 已启动）。

**方式 C：双击脚本启动（推荐 —— 真 nginx，包里自带，无需安装）**

`deploy/nginx-runtime/` 里带了一个 **Windows 版 nginx 1.27.5**（官方二进制，4.6 MB），
不需要你装任何东西。双击 `scripts/start-nginx.bat`：

1. 自动探测后端（8080 → 8081），把地址写进 nginx 配置
2. 从模板 `deploy/nginx.local.conf.tpl` 生成当前机器的 `conf/nginx.conf`
   （端口默认 **8090**，被占用会自动顺延）
3. 启动真正的 nginx（独立进程，关掉黑窗口不会停），并自动打开首页

完整链路：**浏览器 → nginx(8090) 返回页面 → 页面里的 /api/** → nginx 转发给后端(8080) → 返回数据渲染**。

| 脚本 | 作用 |
| --- | --- |
| `scripts/start-nginx.bat` | 启动真 nginx（8090），自动探测后端、生成配置、开浏览器 |
| `scripts/stop-nginx.bat` | 停止 nginx |
| `scripts/start-all.bat` | 一键全起：后端 jar（8080）+ nginx（8090），后端没起会自动拉 jar |
| `scripts/start-backend.bat` | 只起后端 jar 到 8080（不依赖 IDEA，前提：MySQL、Redis 已启动） |

传端口：`start-nginx.bat 8091`。生成的配置与日志在 `deploy/nginx-runtime/conf/` 和 `logs/`。

> 换一台电脑也能用：整个项目目录拷过去（路径建议纯英文，Windows 版 nginx 对中文路径支持差），
> 双击 `start-nginx.bat` 即可 —— 配置里的项目路径是启动时按当前目录动态生成的，不用改任何文件。
>
> 上服务器（Linux）时不用这个 runtime，直接把 `deploy/html` 拷过去 + 用 `deploy/nginx.conf`（listen 80 版）。

**前端开发模式（改代码热更新）**

```bash
cd xinji-erp/web
npm run dev:mall     # http://localhost:5173  商城
npm run dev:admin    # http://localhost:5174  后台
npm run dev:mobile   # http://localhost:5175  手机端
```

三个 dev server 都已配好 `/api` 代理到 `http://127.0.0.1:8080`，所以**后端要先跑起来**。

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

**客户自助注册**：商城与手机端登录页都有「手机号注册」入口。流程是
手机号 → 短信验证码 → 企业名称 + 密码 → 注册成功直接登录（同时建好客户档案）。
演示环境不真发短信，验证码会自动填进输入框；接入阿里云/腾讯云短信后把
`application.yml` 里的 `xinji.sms.mock` 改成 `false` 即可，业务代码不用动。
新注册客户默认授信额度为 0（款到发货模式），运营在后台「客户授信」里审核后放开赊购。
注册相关接口：`/api/public/auth/sms-code`、`/api/public/auth/register`、`/api/public/auth/mobile-check`。

## 5. 目录结构

```
xinji-erp/
├── pom.xml                    # 根聚合 pom（IDEA 打开这一层就能导入）
├── web/                       # 前端工程（Vue 3 + Vite，npm workspaces 三包一体）
│   ├── package.json           # workspaces: shared / mall / admin / mobile
│   ├── shared/                # 三端共用：axios 封装、116 个接口、字典与格式化
│   │   └── src/
│   │       ├── request.js     # 统一请求：剥 {code,msg,data}、401 跳登录、按端存 token
│   │       ├── api.js         # 全部接口封装（client 8 个 / admin 46 个…）
│   │       └── format.js      # 状态字典、金额/吨数/日期格式化
│   ├── mall/                  # 网页商城（8 页）：首页、详情、订单、订单详情、秒杀、我的、登录、注册
│   ├── admin/                 # 运营后台（14 页，Element Plus）：看板、订单、物料、库存…
│   ├── mobile/                # 手机端（7 页）：430px 手机壳 + 底部 tabbar，含注册
│   └── scripts/build-all.mjs  # 三端一次性构建到 deploy/html
├── server/                    # 后端工程（模块化单体）
│   ├── pom.xml
│   ├── Dockerfile             # maven 构建 → JRE 运行
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
├── prototype/                 # 早期高保真静态原型（保留做设计对照，不参与构建）
├── docs/                      # 部署文档、接口文档
├── deploy/
│   ├── nginx.conf             # Linux/Docker 用 nginx 配置（listen 80）
│   ├── nginx.local.conf.tpl   # Windows 本机 nginx 配置模板（脚本按实际路径生成）
│   ├── nginx-runtime/         # Windows 版 nginx 1.27.5（自带，无需安装）
│   ├── Dockerfile             # node 构建三端 → nginx:1.27 托管
│   ├── docker-compose.yml     # mysql + redis + server + web 整套
│   ├── preview.mjs            # 零依赖 Node 预览（无 nginx 时的兜底）
│   └── html/                  # 三端构建产物（npm run build 的输出，nginx 托管的就是它）
├── scripts/                   # 一键启动脚本（Windows .bat + Linux/Mac .sh）
│   ├── start-all.bat          # 一键全起：后端 jar 8080 + nginx 8090
│   ├── start-nginx.bat        # 启动真 nginx（8090），自动探测后端、生成配置
│   ├── stop-nginx.bat         # 停止 nginx
│   ├── start-backend.bat      # 起后端 jar 到 8080（不依赖 IDEA）
│   └── start-web.sh           # Linux / macOS 版（node 预览兜底）
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

## 6.2 前端页面清单（27 页）

**网页商城 `web/mall`（7 页）**

| 路由 | 页面 | 要点 |
| --- | --- | --- |
| `/` | 首页 | 秒杀横幅 + 倒计时、6 个物料卡（双池库存）、采购车侧栏、结算弹窗 |
| `/products/:id` | 物料详情 | 双池四行拆解、加减吨数校验（不能超可售、不能低于起订） |
| `/orders` | 我的订单 | 7 个状态 tab、分页 |
| `/orders/:orderNo` | 订单详情 | 6 步状态进度条、明细结算吨数、取消订单 |
| `/seckill` | 限时抢购 | 活动切换、进度条、抢购后轮询令牌、核销下单 |
| `/mine` | 我的 | 授信使用率进度条、快捷入口、待核销令牌 |
| `/login` | 登录 | 3 个演示客户一键填入、手机号注册入口 |
| `/register` | 注册 | 手机号 + 短信验证码 + 企业信息，60s 重发倒计时，演示验证码自动填入 |

**运营后台 `web/admin`（14 页，Element Plus）**

| 路由 | 页面 | 要点 |
| --- | --- | --- |
| `/` | 经营看板 | 4 个 KPI、订单状态分布、今日待办、库存预警、最近订单 |
| `/orders` | 订单管理 | 多条件筛选、详情抽屉、审核/派车/出厂/签收/取消、派车弹窗 |
| `/products` | 物料档案 | 物料增改、料仓维护、期初入库 |
| `/stock` | 库存管理 | 三 tab（台账/流水/汇总）+ 划拨/回拨/入库 |
| `/purchase` | 采购管理 | 建单、到货入库、详情 |
| `/dispatch` | 调度过磅 | 四 tab（派车单/车辆/司机/看板）、保险到期高亮 |
| `/weigh` | 磅房过磅 | 毛重皮重实时算净重、扣水扣杂、结算金额、磅单列表 |
| `/production` | 生产管理 | 工单、成品率、三列看板、完工报产 |
| `/customers` | 客户与授信 | 授信使用率进度条、调整授信 |
| `/finance` | 财务结算 | 四 tab（对账/应收/收款/发票）+ 收款明细弹窗 |
| `/seckill` | 秒杀活动 | 活动 CRUD、发布/预热/开始/结束回拨、SKU 配置、令牌监控 |
| `/reports` | 报表中心 | 8 个报表 tab，动态列自适应 |
| `/users` | 系统设置 | 账号 CRUD、重置密码、`admin` 停用保护 |
| `/login` | 登录 | 拦非员工账号、4 个演示账号一键填入 |

**手机端 `web/mobile`（6 页）**

| 路由 | 页面 | 要点 |
| --- | --- | --- |
| `/` | 选料 | 分类横向滚动、物料列表、秒杀倒计时横幅 |
| `/products/:id` | 详情 | 底部 sheet 下单 |
| `/seckill` | 抢购 | 抢购 + 令牌 |
| `/orders` | 订单 | 展开式明细 |
| `/mine` | 我的 | 授信 + 令牌 |
| `/login` | 登录 | 拦非客户账号、注册入口 |
| `/register` | 注册 | 手机号 + 验证码 + 企业名称，注册即登录 |

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

### 订单状态机与结算吨数

```
PENDING_AUDIT → AUDITED → DISPATCHED → SHIPPED → SIGNED → SETTLED → INVOICED → PAID
                    ↓          ↓          ↓         ↓
                 CANCELLED（任意未出厂状态可取消，释放冻结库存与授信占用）
```

结算吨数分两条来源：

- **过了磅**：`t_weigh_ticket` 记毛重、皮重、扣水扣杂，算净重后回填 `t_order_item.settle_qty` 与 `settle_amount`，按实际重量结算；
- **没过磅直接签收**：签收时若 `settle_qty` 为空或 0，自动回落为订单吨数，避免「签收单结算吨数是 0」这种脏数据流进对账和报表。

### 三端登录态隔离

三个前端同域名共存，`localStorage` 是共享的，所以 token 分 key 存：
`xj_client_token`（商城 / 手机端共用同一客户身份）、`xj_admin_token`（后台）。
只分 key 还不够 —— 应用启动时会用 `/auth/me` 回填用户资料，避免「token 还在、用户信息丢了」导致界面显示未登录却又能调通接口。
