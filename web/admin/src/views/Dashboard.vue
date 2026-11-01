<template>
  <div>
    <div class="kpi-grid">
      <div class="kpi">
        <div class="k">在办订单</div>
        <div class="v">{{ data.totalOrders || 0 }}<span class="u">单</span></div>
        <div class="s">待审核 {{ data.pendingAudit || 0 }} · 待派车 {{ data.pendingDispatch || 0 }}</div>
      </div>
      <div class="kpi">
        <div class="k">物理总库存</div>
        <div class="v">{{ qtyShort(data.totalStock) }}<span class="u">吨</span></div>
        <div class="s">可售 {{ qtyShort(data.totalSaleable) }} · 活动划拨 {{ qtyShort(data.totalActivityAlloc) }}</div>
      </div>
      <div class="kpi">
        <div class="k">应收未收</div>
        <div class="v">¥{{ money(data.receivableOutstanding) }}</div>
        <div class="s">本月已回款 ¥{{ money(data.monthReceived) }}</div>
      </div>
      <div class="kpi">
        <div class="k">在途 / 生产 / 待开票</div>
        <div class="v">{{ data.inTransit || 0 }}<span class="u">单在途</span></div>
        <div class="s">派车中 {{ data.dispatchingCount || 0 }} · 生产中 {{ data.producingCount || 0 }} · 待开票 {{ data.pendingInvoiceCount || 0 }}</div>
      </div>
    </div>

    <div class="row2">
      <div class="page-card">
        <div class="section-title">订单状态分布</div>
        <div class="dist">
          <div class="d-row" v-for="(cnt, st) in data.statusDistribution || {}" :key="st">
            <span class="d-name">{{ label(ORDER_STATUS, st) }}</span>
            <div class="d-bar"><u :style="{ width: pct(cnt, data.totalOrders), background: barColor(st) }"></u></div>
            <b class="num">{{ cnt }}</b>
          </div>
          <div v-if="!hasDist" class="empty-hint">还没有订单数据</div>
        </div>
      </div>

      <div class="page-card">
        <div class="section-title">今日待办</div>
        <div class="todo" v-for="t in todos" :key="t.k" @click="$router.push(t.to)">
          <div class="t-l">
            <b>{{ t.n }}</b>
            <small>{{ t.d }}</small>
          </div>
          <div class="t-r" :style="{ color: t.color }">{{ t.v }}</div>
        </div>
      </div>
    </div>

    <div class="page-card">
      <div class="section-title">
        库存预警
        <span class="muted small" style="font-weight: 400; margin-left: 4px">低于安全库存线的物料</span>
      </div>
      <el-table :data="data.warnings || []" size="small" v-if="(data.warnings || []).length">
        <el-table-column prop="skuName" label="物料" min-width="140" />
        <el-table-column prop="category" label="品种" width="90" />
        <el-table-column label="可售量" width="110" align="right">
          <template #default="{ row }"><span class="num" style="color: #c0392b">{{ qty(row.saleableQty) }}</span></template>
        </el-table-column>
        <el-table-column label="安全线" width="110" align="right">
          <template #default="{ row }"><span class="num muted">{{ qty(row.safetyQty) }}</span></template>
        </el-table-column>
        <el-table-column label="缺口" width="110" align="right">
          <template #default="{ row }">
            <span class="num" style="color: #c0392b">{{ qty(Number(row.safetyQty) - Number(row.saleableQty)) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="warehouseNo" label="料仓" width="90" />
        <el-table-column label="操作" width="110" align="right">
          <template #default>
            <el-button link type="primary" @click="$router.push('/purchase')">补货</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-hint">所有物料都在安全线以上</div>
    </div>

    <div class="page-card">
      <div class="bar">
        <div class="section-title" style="margin: 0">最近订单</div>
        <div class="spacer"></div>
        <el-button link type="primary" @click="$router.push('/orders')">查看全部 →</el-button>
      </div>
      <el-table :data="recent" size="small" @row-click="goOrder">
        <el-table-column prop="orderNo" label="订单号" width="150">
          <template #default="{ row }"><span class="mono">{{ row.orderNo }}</span></template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户" min-width="150" show-overflow-tooltip />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.orderType === 'SECKILL'" type="danger" size="small">抢购</el-tag>
            <span v-else class="muted small">常规</span>
          </template>
        </el-table-column>
        <el-table-column label="吨数" width="100" align="right">
          <template #default="{ row }"><span class="num">{{ qtyShort(row.totalQty) }}</span></template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }"><span class="money">¥{{ money(row.payAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="tagType(row.status)" size="small">{{ label(ORDER_STATUS, row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="140">
          <template #default="{ row }"><span class="muted small">{{ dateStr(row.createdAt) }}</span></template>
        </el-table-column>
      </el-table>
      <div v-if="!recent.length" class="empty-hint">暂无订单</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api.js'
import { ORDER_STATUS, label, money, qty, qtyShort, dateStr } from '@xinji/shared'

const router = useRouter()
const data = ref({})
const recent = ref([])

const hasDist = computed(() => Object.keys(data.value.statusDistribution || {}).length > 0)

const todos = computed(() => [
  { k: 'audit', n: '待审核订单', d: '审核通过后冻结库存', v: data.value.pendingAudit || 0, to: '/orders', color: '#c98a0a' },
  { k: 'disp', n: '待派车订单', d: '已审核未安排车辆', v: data.value.pendingDispatch || 0, to: '/dispatch', color: '#1b4f6c' },
  { k: 'ship', n: '已出厂待签收', d: '等待客户签收', v: data.value.inTransit || 0, to: '/orders', color: '#2c7499' },
  { k: 'inv', n: '待开发票', d: '客户要求开票', v: data.value.pendingInvoiceCount || 0, to: '/finance', color: '#8e6bb8' }
])

function pct(v, total) {
  const t = Number(total || 0)
  if (!t) return '0%'
  return Math.max(2, (Number(v) / t) * 100).toFixed(0) + '%'
}

function barColor(st) {
  const map = {
    PENDING_AUDIT: '#c98a0a', AUDITED: '#1b4f6c', DISPATCHED: '#2c7499',
    SHIPPED: '#5b7f9a', SIGNED: '#0f8a5f', SETTLED: '#12876a',
    INVOICED: '#4c9a7f', PAID: '#0f8a5f', CANCELLED: '#b4b2a9'
  }
  return map[st] || '#b4b2a9'
}

function tagType(st) {
  const t = (ORDER_STATUS[st] || {}).type
  return t === 'primary' ? '' : t
}

function goOrder(row) {
  router.push('/orders')
}

onMounted(async () => {
  try {
    data.value = (await api.admin.dashboard.summary()) || {}
    recent.value = (await api.admin.dashboard.recent()) || []
  } catch (e) {
    data.value = {}
  }
})
</script>

<style scoped>
.row2 { display: grid; grid-template-columns: 1.4fr 1fr; gap: 14px; margin-top: 14px; }
.dist { display: flex; flex-direction: column; gap: 9px; }
.d-row { display: flex; align-items: center; gap: 12px; font-size: 12.5px; }
.d-name { width: 66px; color: var(--el-text-color-regular); flex: none; }
.d-bar { flex: 1; height: 7px; background: #f0f2f4; border-radius: 4px; overflow: hidden; }
.d-bar u { display: block; height: 100%; border-radius: 4px; }
.d-row b { width: 34px; text-align: right; }
.todo {
  display: flex; align-items: center; padding: 11px 12px; border-radius: 8px;
  cursor: pointer; transition: .15s;
}
.todo:hover { background: #f7f9fa; }
.t-l b { display: block; font-size: 13px; font-weight: 500; }
.t-l small { display: block; font-size: 11.5px; color: var(--el-text-color-secondary); }
.t-r { margin-left: auto; font-size: 21px; font-weight: 600; font-variant-numeric: tabular-nums; }
@media (max-width: 1200px) {
  .row2 { grid-template-columns: 1fr; }
}
</style>
