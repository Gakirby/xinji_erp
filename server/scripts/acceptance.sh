#!/usr/bin/env bash
# =====================================================================
#  吉林鑫基矿业 · 矿石配送 ERP  —— 端到端验收脚本
#  用法：  bash scripts/acceptance.sh          # 默认 http://127.0.0.1:8080
#          BASE=http://192.168.1.10:8080 bash scripts/acceptance.sh
#  依赖：  curl、sed（Windows 用 Git Bash 自带）
#  说明：  每步都会打印接口返回的原始 JSON，按顺序执行即为完整业务闭环
# =====================================================================
set -uo pipefail

B=${BASE:-http://127.0.0.1:8080}
AT=""; CT=""; ONO=""; TK=""; SID=""; RID=""; PID=""

hr() { printf '\n\033[36m── %s ──────────────────────────────────────\033[0m\n' "$1"; }
# 从 JSON 里取单个字符串字段（够用即可，不引入 jq 依赖）
pick() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p" | head -1; }
picknum() { sed -n "s/.*\"$1\":\([0-9][0-9.]*\).*/\1/p" | head -1; }

hr "0. 健康检查"
curl -s -o /dev/null -w "HTTP %{http_code}\n" $B/api/auth/me

hr "1. 员工登录（ERP 运营后台）"
R=$(curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123","userType":"EMPLOYEE"}')
echo "$R"
AT=$(echo "$R" | pick token)
[ -z "$AT" ] && { echo "登录失败，后续步骤终止"; exit 1; }

hr "2. 客户登录（网页商城 / 小程序共用同一个 customer_id）"
R=$(curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"yatai","password":"123456","userType":"CUSTOMER"}')
echo "$R"
CT=$(echo "$R" | pick token)

hr "3. 客户端商品列表（双池：可售 / 活动 / 冻结）"
curl -s $B/api/client/products -H "Authorization: Bearer $CT"
echo

hr "4. 三端隔离验证：客户 token 访问后台接口"
curl -s "$B/api/admin/orders?page=1&size=1" -H "Authorization: Bearer $CT"
echo
hr "4b. 无凭证访问后台接口"
curl -s "$B/api/admin/orders?page=1&size=1"
echo

hr "5. 采购建单（供应商 龙潭采石场，石灰石 10-20mm，200 吨）"
R=$(curl -s -X POST $B/api/admin/purchase/orders -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' \
  -d '{"supplierName":"龙潭采石场","skuId":2,"planQty":200,"price":58.00,"expectedDate":"2026-11-08"}')
echo "$R"
PID=$(echo "$R" | picknum id)

hr "6. 采购到货入库 120 吨（自动累加到货量 + 写库存台账）"
curl -s -X POST $B/api/admin/purchase/orders/$PID/arrive -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' -d '{"arrivedQty":120,"remark":"第一车到货"}'
echo
curl -s "$B/api/admin/stock/flows?skuId=2&page=1&size=1" -H "Authorization: Bearer $AT"
echo

hr "7. 客户端下单 30 吨（提交即冻结销售可售池）"
R=$(curl -s -X POST $B/api/client/orders -H "Authorization: Bearer $CT" \
  -H 'Content-Type: application/json' \
  -d '{"deliveryType":"DELIVERY","expectedDate":"11-05 08:00 — 12:00","receiver":"张伟","address":"吉林省长春市二道区英俊镇建龙北路18号","items":[{"skuId":1,"qty":30}],"remark":"验收测试单"}')
echo "$R"
ONO=$(echo "$R" | pick orderNo)

hr "8. 超卖拦截：下单 99999 吨"
curl -s -X POST $B/api/client/orders -H "Authorization: Bearer $CT" \
  -H 'Content-Type: application/json' \
  -d '{"deliveryType":"DELIVERY","expectedDate":"11-05","receiver":"张伟","address":"长春","items":[{"skuId":1,"qty":99999}]}'
echo

hr "9. 审核 → 派车 → 出厂（出厂核销冻结量）"
for s in audit dispatch ship; do
  printf '%-10s ' "$s:"
  curl -s -X POST $B/api/admin/orders/$ONO/$s -H "Authorization: Bearer $AT"; echo
done
curl -s $B/api/client/products -H "Authorization: Bearer $CT"
echo

hr "10. 过磅出票（毛重 52.4 / 皮重 17.2 / 含水 1.1% / 扣杂 0.15）"
curl -s -X POST $B/api/admin/weigh/tickets -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' \
  -d "{\"orderNo\":\"$ONO\",\"skuId\":1,\"plateNo\":\"吉B·9T001\",\"driverName\":\"赵师傅\",\"driverPhone\":\"13900008811\",\"grossWeight\":52.400,\"tareWeight\":17.200,\"waterRate\":0.011,\"impurityDeductWeight\":0.150,\"weighman\":\"王立\"}"
echo
hr "10b. 结算量已回填到订单明细"
curl -s $B/api/admin/orders/$ONO -H "Authorization: Bearer $AT"
echo

hr "11. 客户签收"
curl -s -X POST $B/api/admin/orders/$ONO/sign -H "Authorization: Bearer $AT"; echo

hr "12. 生成对账批次（客户 2 吉林建龙，账期 11-01）"
R=$(curl -s -X POST $B/api/admin/finance/settlements -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' \
  -d '{"customerId":2,"periodStart":"2026-11-01","periodEnd":"2026-11-01"}')
echo "$R"
SID=$(echo "$R" | picknum id)

hr "13. 对账明细"
curl -s $B/api/admin/finance/settlements/$SID/items -H "Authorization: Bearer $AT"; echo

hr "14. 客户确认对账 → 自动生成应收"
R=$(curl -s -X POST $B/api/admin/finance/settlements/$SID/confirm -H "Authorization: Bearer $AT")
echo "$R"
RID=$(echo "$R" | picknum id)

hr "15. 收款登记 2000 元"
curl -s -X POST "$B/api/admin/finance/receivables/$RID/receive?amount=2000" -H "Authorization: Bearer $AT"; echo

hr "16. 秒杀活动预热（活动库存装载进 Redis）"
curl -s -X POST $B/api/admin/seckill/activities/1/warmup -H "Authorization: Bearer $AT"; echo

hr "17. 秒杀活动 SKU（活动池与销售池独立）"
curl -s $B/api/client/seckill/activities/1/skus -H "Authorization: Bearer $CT"; echo

hr "18. 抢购 20 吨石灰石 5-10mm（Redis Lua 原子扣减）"
R=$(curl -s -X POST $B/api/client/seckill/grab -H "Authorization: Bearer $CT" \
  -H 'Content-Type: application/json' -d '{"seckillSkuId":2,"qty":20}')
echo "$R"
TK=$(echo "$R" | pick tokenNo)

hr "19. 一人一单：重复抢购被拦"
curl -s -X POST $B/api/client/seckill/grab -H "Authorization: Bearer $CT" \
  -H 'Content-Type: application/json' -d '{"seckillSkuId":2,"qty":5}'; echo

hr "20. 令牌异步发放结果（Redis Stream 消费后落库，状态 ISSUED）"
# 异步发放：正常 200ms 内完成；若刚清过 Redis，消费者组重建最多需要几秒
for i in 1 2 3 4 5 6 7 8 9 10; do
  R=$(curl -s $B/api/client/seckill/tokens/$TK -H "Authorization: Bearer $CT")
  echo "$R" | grep -q '"status":"ISSUED"' && break
  sleep 1
done
echo "$R"

hr "21. 令牌兑换下单（扣活动池，不动销售池）"
curl -s -X POST "$B/api/client/seckill/tokens/$TK/order" -H "Authorization: Bearer $CT" \
  -H 'Content-Type: application/json' \
  -d '{"deliveryType":"DELIVERY","expectedDate":"11-05 08:00 — 12:00","receiver":"张伟","address":"吉林省长春市二道区英俊镇建龙北路18号"}'
echo
hr "21b. 双池校验：活动划拨量应减少 20 吨，可售量不变"
curl -s $B/api/client/products -H "Authorization: Bearer $CT"; echo

hr "22. 令牌重复使用拦截"
curl -s -X POST "$B/api/client/seckill/tokens/$TK/order" -H "Authorization: Bearer $CT" \
  -H 'Content-Type: application/json' -d '{"deliveryType":"DELIVERY","expectedDate":"11-05"}'; echo

hr "23. 秒杀监控（消费者存活 / 积压数）"
curl -s $B/api/admin/seckill/monitor -H "Authorization: Bearer $AT"; echo

hr "24. 后台工作台 KPI"
curl -s $B/api/admin/dashboard/summary -H "Authorization: Bearer $AT"; echo
hr "24b. 订单按状态汇总"
curl -s $B/api/admin/dashboard/orders/by-status -H "Authorization: Bearer $AT"; echo

hr "25. 库存台账（最近 8 条）"
curl -s "$B/api/admin/stock/flows?page=1&size=8" -H "Authorization: Bearer $AT"; echo

# =====================================================================
#  第二部分：基础档案 / 调度 / 生产 / 报表 / 发票 / 账号
# =====================================================================

hr "26. 客户分页与授信占用"
curl -s "$B/api/admin/customers?page=1&size=5" -H "Authorization: Bearer $AT"; echo

hr "27. 新建客户 + 调整授信"
R=$(curl -s -X POST $B/api/admin/customers -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' \
  -d '{"customerName":"桦甸宏达建材有限公司","contactName":"马涛","contactPhone":"13904327788","address":"吉林省桦甸市经济开发区","creditLimit":200000}')
echo "$R"
NEWCID=$(echo "$R" | picknum id)
curl -s -X POST "$B/api/admin/customers/$NEWCID/credit?creditLimit=150000" -H "Authorization: Bearer $AT"; echo
hr "27b. 授信额度不能为负（预期报错）"
curl -s -X POST "$B/api/admin/customers/$NEWCID/credit?creditLimit=-1" -H "Authorization: Bearer $AT"; echo

hr "28. 客户详情（授信可用 / 占用率 / 是否已开账号）"
curl -s "$B/api/admin/customers/$NEWCID" -H "Authorization: Bearer $AT"; echo

hr "29. 物料建档（带 50 吨期初库存，自动写 INBOUND 台账）"
R=$(curl -s -X POST $B/api/admin/products -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' \
  -d '{"skuName":"石灰石 30-50mm","category":"石灰石","spec":"压碎值≤12%","price":75.00,"warehouseId":1,"safetyQty":100,"initQty":50}')
echo "$R"
NEWSKU=$(echo "$R" | picknum id)
hr "29b. 新物料台账（应有 INBOUND 50 吨）"
curl -s "$B/api/admin/stock/flows?skuId=$NEWSKU&page=1&size=3" -H "Authorization: Bearer $AT"; echo
hr "29c. 下架物料"
curl -s -X POST "$B/api/admin/products/$NEWSKU/status?status=0" -H "Authorization: Bearer $AT"; echo

hr "30. 料仓列表"
curl -s "$B/api/admin/products/warehouses" -H "Authorization: Bearer $AT"; echo

hr "31. 车辆与司机档案"
curl -s "$B/api/admin/dispatch/vehicles" -H "Authorization: Bearer $AT"; echo
curl -s "$B/api/admin/dispatch/drivers" -H "Authorization: Bearer $AT"; echo

hr "32. 保险到期提醒（30 天内）"
curl -s "$B/api/admin/dispatch/vehicles/insurance-warnings" -H "Authorization: Bearer $AT"; echo

hr "33. 未审核订单派车（预期报错）"
curl -s -X POST $B/api/admin/dispatch/orders -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' \
  -d '{"orderNo":"XJ20261101005","vehicleId":1,"driverId":1,"planQty":30}'; echo

hr "34. 审核订单 4 后派车（订单自动推进到已派车）"
curl -s -X POST $B/api/admin/orders/XJ20261101004/audit -H "Authorization: Bearer $AT"; echo
R=$(curl -s -X POST $B/api/admin/dispatch/orders -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' \
  -d '{"orderNo":"XJ20261101004","vehicleId":4,"driverId":4,"planQty":30,"planTime":"2026-11-03 07:30:00","remark":"验收派车"}')
echo "$R"
NEWDID=$(echo "$R" | picknum id)
hr "34b. 订单状态应为 DISPATCHED"
curl -s "$B/api/admin/orders/XJ20261101004" -H "Authorization: Bearer $AT"; echo

hr "35. 重复派车 / 超载派车（预期报错）"
curl -s -X POST $B/api/admin/dispatch/orders -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' \
  -d '{"orderNo":"XJ20261101004","vehicleId":1,"driverId":1,"planQty":10}'; echo
curl -s -X POST $B/api/admin/orders/XJ20261101005/audit -H "Authorization: Bearer $AT" > /dev/null
curl -s -X POST $B/api/admin/dispatch/orders -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' \
  -d '{"orderNo":"XJ20261101005","vehicleId":3,"driverId":3,"planQty":45}'; echo

hr "36. 派车单转装车中 + 调度看板"
curl -s -X POST "$B/api/admin/dispatch/orders/$NEWDID/loading" -H "Authorization: Bearer $AT"; echo
curl -s "$B/api/admin/dispatch/board" -H "Authorization: Bearer $AT"; echo

hr "37. 生产工单：建单 → 开工 → 完工报产（成品入销售可售池）"
R=$(curl -s -X POST $B/api/admin/production/orders -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' \
  -d '{"lineName":"一号破碎线","shift":"夜班","skuId":5,"planQty":200,"feedQty":230,"operator":"王立","remark":"验收工单"}')
echo "$R"
NEWPID=$(echo "$R" | picknum id)
curl -s -X POST "$B/api/admin/production/orders/$NEWPID/start" -H "Authorization: Bearer $AT"; echo
hr "37b. 完工前 石粉 可售量"
curl -s "$B/api/admin/products" -H "Authorization: Bearer $AT"; echo
curl -s -X POST "$B/api/admin/production/orders/$NEWPID/finish" -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' -d '{"outputQty":180,"feedQty":228,"remark":"成品率 78.9%"}'; echo
hr "37c. 完工后 石粉 可售量应 +180，且台账多一条 INBOUND"
curl -s "$B/api/admin/products" -H "Authorization: Bearer $AT"; echo
curl -s "$B/api/admin/stock/flows?skuId=5&page=1&size=2" -H "Authorization: Bearer $AT"; echo
hr "37d. 重复报产（预期报错）"
curl -s -X POST "$B/api/admin/production/orders/$NEWPID/finish" -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' -d '{"outputQty":10}'; echo

hr "38. 生产看板（在产 / 总产量 / 成品率）"
curl -s "$B/api/admin/production/board" -H "Authorization: Bearer $AT"; echo

hr "39. 报表中心：经营总览"
curl -s "$B/api/admin/reports/overview" -H "Authorization: Bearer $AT"; echo
hr "39b. 销售日报"
curl -s "$B/api/admin/reports/sales/daily?from=2026-10-30&to=2026-11-01" -H "Authorization: Bearer $AT"; echo
hr "39c. 应收账龄"
curl -s "$B/api/admin/reports/receivable/aging" -H "Authorization: Bearer $AT"; echo

hr "40. 发票：从应收单开票 → 开具 → 统计"
R=$(curl -s -X POST $B/api/admin/finance/invoices -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' \
  -d '{"receivableId":3,"invoiceType":"VAT_SPECIAL","taxRate":0.13,"remark":"验收开票"}')
echo "$R"
NEWINV=$(echo "$R" | picknum id)
hr "40b. 重复开票（预期报错）"
curl -s -X POST $B/api/admin/finance/invoices -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' -d '{"receivableId":3}'; echo
curl -s -X POST "$B/api/admin/finance/invoices/$NEWINV/issue" -H "Authorization: Bearer $AT"; echo
curl -s "$B/api/admin/finance/invoices/stats" -H "Authorization: Bearer $AT"; echo

hr "41. 收款登记（带收款方式，落收款流水）"
curl -s -G -X POST "$B/api/admin/finance/receivables/3/receive-detail" \
  --data-urlencode "amount=20000" --data-urlencode "payMethod=CHECK" \
  --data-urlencode "remark=银行承兑汇票" -H "Authorization: Bearer $AT"; echo
hr "41b. 收款流水"
curl -s "$B/api/admin/finance/payments" -H "Authorization: Bearer $AT"; echo
hr "41c. 回款统计"
curl -s "$B/api/admin/finance/payments/stats" -H "Authorization: Bearer $AT"; echo

hr "42. 账号：新建员工 → 登录 → 停用 → 重置密码"
R=$(curl -s -X POST $B/api/admin/users -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' \
  -d '{"username":"zhaowei","password":"123456","mobile":"13904329988","userType":"EMPLOYEE","role":"SALES","employeeName":"赵伟"}')
echo "$R"
NEWUID=$(echo "$R" | picknum id)
curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"zhaowei","password":"123456","userType":"EMPLOYEE"}'; echo
hr "42b. 客户账号必须绑定 customer_id（预期报错）"
curl -s -X POST $B/api/admin/users -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' \
  -d '{"username":"hd_user","password":"123456","userType":"CUSTOMER"}'; echo
hr "42c. 给新客户开账号（网页商城 / 小程序共用同一 customer_id）"
curl -s -X POST $B/api/admin/users -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"hd_user\",\"password\":\"123456\",\"userType\":\"CUSTOMER\",\"customerId\":$NEWCID}"; echo
curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"hd_user","password":"123456","userType":"CUSTOMER"}'; echo
hr "42d. 停用后不能登录（预期 403）+ 内置 admin 不允许停用"
curl -s -X POST "$B/api/admin/users/$NEWUID/status?status=0" -H "Authorization: Bearer $AT"; echo
curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"zhaowei","password":"123456","userType":"EMPLOYEE"}'; echo
curl -s -X POST "$B/api/admin/users/1/status?status=0" -H "Authorization: Bearer $AT"; echo
curl -s -X POST "$B/api/admin/users/$NEWUID/status?status=1" -H "Authorization: Bearer $AT"; echo
hr "42e. 重置密码后用新密码登录"
curl -s -X POST "$B/api/admin/users/$NEWUID/reset-password?newPassword=abc123456" -H "Authorization: Bearer $AT"; echo
curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"zhaowei","password":"abc123456","userType":"EMPLOYEE"}'; echo

hr "43. 秒杀运营：新建活动 → 追加物料（划拨 50 吨）→ 发布 → 结束回拨"
R=$(curl -s -X POST $B/api/admin/seckill/activities -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' \
  -d '{"activityName":"验收测试 · 夜场限时抢","startTime":"2026-11-01 20:00:00","endTime":"2026-11-02 23:00:00"}')
echo "$R"
NEWAID=$(echo "$R" | picknum id)
hr "43b. 活动价高于挂牌价（预期报错）"
curl -s -X POST "$B/api/admin/seckill/activities/$NEWAID/skus" -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' \
  -d '{"skuId":2,"seckillPrice":99,"activityStock":50,"limitPerUser":10}'; echo
hr "43c. 追加物料：活动价 60，划拨 50 吨（销售可售池 -50）"
curl -s -X POST "$B/api/admin/seckill/activities/$NEWAID/skus" -H "Authorization: Bearer $AT" \
  -H 'Content-Type: application/json' \
  -d '{"skuId":2,"seckillPrice":60,"activityStock":50,"limitPerUser":10}'; echo
curl -s "$B/api/admin/products" -H "Authorization: Bearer $AT"; echo
hr "43d. 发布 + 预热"
curl -s -X POST "$B/api/admin/seckill/activities/$NEWAID/publish" -H "Authorization: Bearer $AT"; echo
curl -s -X POST "$B/api/admin/seckill/activities/$NEWAID/warmup" -H "Authorization: Bearer $AT"; echo
hr "43e. 结束活动：未售出 50 吨回拨销售池（可售量恢复）"
curl -s -X POST "$B/api/admin/seckill/activities/$NEWAID/finish" -H "Authorization: Bearer $AT"; echo
curl -s "$B/api/admin/products" -H "Authorization: Bearer $AT"; echo

hr "44. 工作台 KPI（含调度 / 生产 / 开票待办）"
curl -s $B/api/admin/dashboard/summary -H "Authorization: Bearer $AT"; echo

printf '\n\033[32m验收流程执行完毕。\033[0m\n'
printf '预期报错（业务规则拦截，均为 4xx/5xx + 中文原因）：\n'
printf '  第 4 步 403 越权；第 4b 步 401 无凭证；第 8 步 可售库存不足；\n'
printf '  第 19 步 每人限抢一份；第 22 步 令牌已使用；第 27b 步 授信不能为负；\n'
printf '  第 33 步 只有已审核订单才能派车；第 35 步 重复派车 / 超载；\n'
printf '  第 37d 步 已完工不能重复报产；第 40b 步 不能重复开票；\n'
printf '  第 42b 步 客户账号必须绑客户；第 42d 步 停用不能登录 + admin 禁停；\n'
printf '  第 43b 步 活动价必须低于挂牌价。\n'
printf '关键数值：第 21b 步活动划拨比第 17 步少 20 吨且可售量不变；\n'
printf '  第 29 步新物料可售 50 吨；第 37c 步石粉可售 +180 吨；\n'
printf '  第 43c 步可售 -50 吨、第 43e 步回拨后可售恢复。\n'
