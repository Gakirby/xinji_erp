<template>
  <div class="page-card">
    <el-tabs v-model="tab" @tab-change="load">
      <el-tab-pane v-for="t in tabs" :key="t.k" :label="t.n" :name="t.k">
        <div v-loading="loading">
          <div class="bar" v-if="t.note">
            <span class="muted small">{{ t.note }}</span>
            <div class="spacer"></div>
            <el-button size="small" @click="load">刷新</el-button>
          </div>

          <div v-if="!rows.length && !loading" class="empty-hint">该报表暂无数据</div>

          <el-table v-else :data="rows" size="small" border max-height="560">
            <el-table-column
              v-for="c in cols"
              :key="c"
              :prop="c"
              :label="fieldLabel(c)"
              :align="isNumberCol(c) ? 'right' : 'left'"
              min-width="110"
            >
              <template #default="{ row }">
                <span :class="{ num: isNumberCol(c), money: isMoneyCol(c) }">{{ display(row[c], c) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api.js'
import { money, qty } from '@xinji/shared'

const tab = ref('overview')
const rows = ref([])
const cols = ref([])
const loading = ref(false)

const tabs = [
  { k: 'overview', n: '经营总览', note: '本期销量、金额、客户数与库存周转' },
  { k: 'salesDaily', n: '销售日报', note: '按天统计下单量、结算量与金额' },
  { k: 'salesCustomers', n: '客户排行', note: '按客户汇总销量与回款' },
  { k: 'salesSkus', n: '物料排行', note: '按物料汇总销量与金额' },
  { k: 'stockFlows', n: '出入库汇总', note: '按业务类型统计库存变动' },
  { k: 'receivableAging', n: '应收账龄', note: '按账龄区间分布应收余额' },
  { k: 'orderStatus', n: '订单状态', note: '各状态订单数量与吨数' },
  { k: 'seckillEffect', n: '秒杀效果', note: '活动成交、令牌核销与转化' }
]

const fieldNames = {
  statDate: '日期', day: '日期', date: '日期',
  customerId: '客户ID', customerName: '客户',
  skuId: '物料ID', skuName: '物料', skuNo: '物料编码',
  orderCount: '订单数', cnt: '数量', count: '数量',
  qty: '吨数', totalQty: '吨数', settleQty: '结算吨数',
  amount: '金额', totalAmount: '金额', payAmount: '应付金额',
  receivedAmount: '已收金额', outstanding: '未收金额',
  bizType: '业务类型', status: '状态',
  bucket: '账龄区间', aging: '账龄',
  activityName: '活动', activityNo: '活动编号',
  soldQty: '已抢吨数', activityStock: '活动库存',
  tokenCount: '令牌数', usedCount: '已核销', rate: '转化率',
  totalStock: '总库存', saleableQty: '可售量', activityAllocQty: '活动划拨', frozenQty: '冻结',
  avgPrice: '均价', customerCount: '客户数', skuCount: '物料数'
}

const moneyFields = ['amount', 'totalAmount', 'payAmount', 'receivedAmount', 'outstanding', 'arrivedAmount', 'avgPrice']

function fieldLabel(k) {
  return fieldNames[k] || k
}

function isNumberCol(k) {
  const sample = rows.value.length ? rows.value[0][k] : null
  return typeof sample === 'number'
}

function isMoneyCol(k) {
  return moneyFields.includes(k)
}

function display(v, k) {
  if (v === null || v === undefined || v === '') return '-'
  if (typeof v === 'object') return JSON.stringify(v)
  if (isMoneyCol(k) && !Number.isNaN(Number(v))) return '¥' + money(v)
  if (typeof v === 'number' || (!Number.isNaN(Number(v)) && String(v).trim() !== '' && /^-?\d+(\.\d+)?$/.test(String(v)))) {
    const n = Number(v)
    if (['qty', 'totalQty', 'settleQty', 'soldQty', 'activityStock', 'totalStock', 'saleableQty', 'activityAllocQty', 'frozenQty'].includes(k)) {
      return qty(n)
    }
    return Number.isInteger(n) ? String(n) : n.toFixed(2)
  }
  return String(v)
}

async function load() {
  loading.value = true
  rows.value = []
  cols.value = []
  try {
    const data = await api.admin.report[tab.value]()
    if (Array.isArray(data)) {
      rows.value = data
      cols.value = data.length ? Object.keys(data[0]) : []
    } else if (data && typeof data === 'object') {
      rows.value = Object.keys(data).map((k) => ({ 指标: fieldLabel(k), 数值: data[k] }))
      cols.value = ['指标', '数值']
    }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
