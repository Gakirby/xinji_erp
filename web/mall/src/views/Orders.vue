<template>
  <div>
    <div class="between" style="margin-bottom: 18px">
      <h1 class="page-title">我的订单</h1>
      <RouterLink class="btn btn-primary btn-sm" to="/">继续选料</RouterLink>
    </div>

    <div class="tabs">
      <button v-for="t in tabs" :key="t.v" :class="{ on: status === t.v }" @click="switchTab(t.v)">
        {{ t.n }}
      </button>
    </div>

    <div class="card" style="margin-top: 14px">
      <div class="loading" v-if="loading">加载中…</div>
      <div class="empty" v-else-if="!list.length">
        还没有订单<br /><span class="small">去首页选料下单，或参加限时抢购</span>
      </div>
      <table class="table" v-else>
        <thead>
          <tr>
            <th>订单号</th>
            <th>下单时间</th>
            <th>物料</th>
            <th class="num">吨数</th>
            <th class="num">应付金额</th>
            <th>状态</th>
            <th style="width: 150px"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="o in list" :key="o.id">
            <td class="mono" style="font-size: 12.5px">{{ o.orderNo }}</td>
            <td class="muted small">{{ dateStr(o.createdAt) }}</td>
            <td>
              <span v-if="o.orderType === 'SECKILL'" class="tag tag-red" style="margin-right: 6px">抢购</span>
              {{ skuNames[o.orderNo] || '-' }}
            </td>
            <td class="num mono">{{ qtyShort(o.totalQty) }}</td>
            <td class="num money">¥{{ money(o.payAmount) }}</td>
            <td>
              <span class="tag" :class="'tag-' + statusType(ORDER_STATUS, o.status)">
                {{ label(ORDER_STATUS, o.status) }}
              </span>
            </td>
            <td style="text-align: right">
              <RouterLink class="btn btn-sm" :to="'/orders/' + o.orderNo">详情</RouterLink>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="pager" v-if="total > size">
      <button class="btn btn-sm" :disabled="page <= 1" @click="go(page - 1)">上一页</button>
      <span class="muted small">第 {{ page }} / {{ Math.ceil(total / size) }} 页 · 共 {{ total }} 单</span>
      <button class="btn btn-sm" :disabled="page >= Math.ceil(total / size)" @click="go(page + 1)">下一页</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '../api.js'
import { ORDER_STATUS, label, statusType, money, qtyShort, dateStr } from '@xinji/shared'

const tabs = [
  { n: '全部', v: '' },
  { n: '待审核', v: 'PENDING_AUDIT' },
  { n: '已审核', v: 'AUDITED' },
  { n: '已出厂', v: 'SHIPPED' },
  { n: '已签收', v: 'SIGNED' },
  { n: '已对账', v: 'SETTLED' },
  { n: '已取消', v: 'CANCELLED' }
]

const list = ref([])
const skuNames = ref({})
const total = ref(0)
const page = ref(1)
const size = 10
const status = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await api.client.order.list({ status: status.value || undefined, page: page.value, size })
    list.value = data.records || data.list || []
    total.value = data.total || 0
    loadNames()
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function loadNames() {
  const missing = list.value.filter((o) => !skuNames.value[o.orderNo])
  for (const o of missing.slice(0, 6)) {
    try {
      const d = await api.client.order.detail(o.orderNo)
      const items = (d.items || []).map((i) => i.skuName)
      skuNames.value[o.orderNo] = items.join(' / ') || '-'
    } catch (e) {
      skuNames.value[o.orderNo] = '-'
    }
  }
}

function switchTab(v) {
  status.value = v
  page.value = 1
  load()
}

function go(p) {
  page.value = p
  load()
}

onMounted(load)
</script>

<style scoped>
.page-title { font-size: 20px; font-weight: 600; }
.tabs { display: flex; gap: 6px; flex-wrap: wrap; }
.tabs button {
  padding: 7px 15px; border-radius: 8px; font-size: 13px; color: var(--ink2);
  border: 1px solid var(--line); background: #fff;
}
.tabs button.on { background: var(--brand); border-color: var(--brand); color: #fff; font-weight: 500; }
.pager { display: flex; align-items: center; justify-content: center; gap: 16px; margin-top: 20px; }
</style>
