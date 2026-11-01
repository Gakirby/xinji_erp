<template>
  <div>
    <div class="tabs">
      <button v-for="t in tabs" :key="t.v" :class="{ on: status === t.v }" @click="switchTab(t.v)">{{ t.n }}</button>
    </div>

    <div class="loading" v-if="loading">加载中…</div>
    <div class="empty" v-else-if="!list.length">还没有订单</div>

    <div class="o" v-for="o in list" :key="o.id">
      <div class="between">
        <span class="mono tiny">{{ o.orderNo }}</span>
        <span class="tag" :class="'tag-' + statusType(ORDER_STATUS, o.status)">{{ label(ORDER_STATUS, o.status) }}</span>
      </div>
      <div class="mid">
        <div>
          <b>{{ names[o.orderNo] || '查看明细' }}</b>
          <small class="muted tiny">
            {{ qtyShort(o.totalQty) }} 吨 · {{ dateStr(o.createdAt) }}
            <span v-if="o.orderType === 'SECKILL'" class="tag tag-red" style="margin-left: 4px">抢购</span>
          </small>
        </div>
        <b class="money" style="color: var(--red)">¥{{ money(o.payAmount) }}</b>
      </div>
      <div class="acts">
        <button class="btn btn-sm" @click="detail(o)">查看明细</button>
        <button v-if="['PENDING_AUDIT', 'AUDITED'].includes(o.status)" class="btn btn-sm" @click="cancel(o)">取消订单</button>
      </div>
      <div class="sub" v-if="expanded === o.orderNo">
        <div class="li" v-for="it in items" :key="it.id">
          <span>{{ it.skuName }}</span>
          <span>{{ qtyShort(it.qty) }} 吨 · ¥{{ money(it.amount) }}</span>
        </div>
        <div class="li" v-if="!items.length"><span class="muted tiny">加载中…</span></div>
      </div>
    </div>

    <div class="toast" :class="{ err: toastErr }" v-if="toast">{{ toast }}</div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api.js'
import { ORDER_STATUS, label, statusType, money, qtyShort, dateStr } from '@xinji/shared'

const route = useRoute()
const list = ref([])
const items = ref([])
const names = ref({})
const loading = ref(false)
const status = ref('')
const expanded = ref('')
const toast = ref('')
const toastErr = ref(false)

const tabs = [
  { n: '全部', v: '' },
  { n: '待审核', v: 'PENDING_AUDIT' },
  { n: '已出厂', v: 'SHIPPED' },
  { n: '已签收', v: 'SIGNED' }
]

function tip(msg, err) {
  toast.value = msg
  toastErr.value = !!err
  setTimeout(() => (toast.value = ''), 2400)
}

async function load() {
  loading.value = true
  try {
    const data = await api.client.order.list({ status: status.value || undefined, page: 1, size: 20 })
    list.value = data.records || data.list || []
    fillNames()
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function fillNames() {
  for (const o of list.value) {
    if (names.value[o.orderNo]) continue
    try {
      const d = await api.client.order.detail(o.orderNo)
      names.value[o.orderNo] = (d.items || []).map((i) => i.skuName).join(' / ')
    } catch (e) { /* ignore */ }
  }
}

async function detail(o) {
  if (expanded.value === o.orderNo) {
    expanded.value = ''
    return
  }
  expanded.value = o.orderNo
  items.value = []
  try {
    const d = await api.client.order.detail(o.orderNo)
    items.value = d.items || []
  } catch (e) { /* ignore */ }
}

async function cancel(o) {
  if (!confirm('确定取消这笔订单？')) return
  try {
    await api.client.order.cancel(o.orderNo, '客户主动取消')
    tip('已取消')
    load()
  } catch (e) {
    tip(e.message, true)
  }
}

function switchTab(v) {
  status.value = v
  load()
}

onMounted(() => {
  load()
  if (route.query.new) tip('下单成功，等待矿区审核')
})
</script>

<style scoped>
.tabs { display: flex; gap: 7px; margin-bottom: 10px; overflow-x: auto; }
.tabs button {
  padding: 5px 13px; border-radius: 20px; font-size: 12.5px; white-space: nowrap;
  background: #fff; border: 1px solid var(--line); color: var(--ink2);
}
.tabs button.on { background: var(--brand); border-color: var(--brand); color: #fff; }
.o { background: #fff; border-radius: 11px; padding: 13px; margin-bottom: 10px; }
.mid { display: flex; align-items: center; justify-content: space-between; margin: 10px 0; gap: 10px; }
.mid b { font-size: 13.5px; }
.mid small { display: block; }
.acts { display: flex; gap: 8px; }
.acts .btn { flex: 1; }
.sub { margin-top: 10px; padding-top: 10px; border-top: 1px dashed var(--line); }
.li { display: flex; justify-content: space-between; font-size: 12px; padding: 4px 0; color: var(--ink2); }
.loading { padding: 40px; text-align: center; color: var(--muted); font-size: 13px; }
</style>
