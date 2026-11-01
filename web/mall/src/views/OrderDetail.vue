<template>
  <div class="loading" v-if="loading">加载中…</div>
  <div class="empty" v-else-if="!order">订单不存在</div>
  <div v-else>
    <div class="back" @click="$router.push('/orders')">← 返回订单列表</div>

    <div class="card head">
      <div class="between">
        <div>
          <div class="row" style="gap: 10px">
            <h1 class="mono">{{ order.orderNo }}</h1>
            <span class="tag" :class="'tag-' + statusType(ORDER_STATUS, order.status)">
              {{ label(ORDER_STATUS, order.status) }}
            </span>
            <span v-if="order.orderType === 'SECKILL'" class="tag tag-red">抢购订单</span>
          </div>
          <p class="muted small" style="margin-top: 6px">
            下单时间 {{ dateStr(order.createdAt) }} · {{ order.deliveryType === 'PICKUP' ? '客户自提' : '矿区配送' }}
          </p>
        </div>
        <div class="amount">
          <span class="muted small">应付金额</span>
          <b class="money">¥{{ money(order.payAmount) }}</b>
        </div>
      </div>

      <div class="steps">
        <div class="step" v-for="(s, i) in steps" :key="s.k" :class="{ done: i <= activeStep, cur: i === activeStep }">
          <div class="dot"></div>
          <span>{{ s.n }}</span>
        </div>
      </div>
    </div>

    <div class="grid">
      <div class="card">
        <div class="card-h"><h3>物料明细</h3></div>
        <table class="table">
          <thead>
            <tr>
              <th>物料</th>
              <th>规格</th>
              <th class="num">下单吨数</th>
              <th class="num">单价</th>
              <th class="num">金额</th>
              <th class="num">结算吨数</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="it in order.items || []" :key="it.id">
              <td>{{ it.skuName }}</td>
              <td class="muted small">{{ it.spec }}</td>
              <td class="num mono">{{ qty(it.qty) }}</td>
              <td class="num mono">¥{{ money(it.price) }}</td>
              <td class="num money">¥{{ money(it.amount) }}</td>
              <td class="num mono" :style="{ color: Number(it.settleQty) > 0 ? 'var(--green)' : 'var(--muted)' }">
                {{ Number(it.settleQty) > 0 ? qty(it.settleQty) : '待过磅' }}
              </td>
            </tr>
          </tbody>
        </table>
        <div class="foot-sum">
          <div><span class="muted">商品金额</span><b class="money">¥{{ money(order.totalAmount) }}</b></div>
          <div><span class="muted">运费</span><b>{{ Number(order.freight) ? '¥' + money(order.freight) : '免运费' }}</b></div>
          <div><span class="muted">优惠</span><b style="color: var(--green)">-¥{{ money(order.discountAmount) }}</b></div>
          <div class="tot"><span>应付合计</span><b class="money">¥{{ money(order.payAmount) }}</b></div>
        </div>
      </div>

      <div class="side">
        <div class="card card-pad">
          <h3 class="side-h">收货信息</h3>
          <div class="kv"><span>收货人</span><b>{{ order.receiver || '-' }}</b></div>
          <div class="kv"><span>联系电话</span><b>{{ order.contactPhone || auth.user?.mobile || '-' }}</b></div>
          <div class="kv"><span>收货地址</span><b class="addr">{{ order.address || '-' }}</b></div>
          <div class="kv"><span>期望到货</span><b>{{ order.expectedDate || '-' }}</b></div>
          <div class="kv" v-if="order.remark"><span>备注</span><b class="addr">{{ order.remark }}</b></div>
        </div>

        <div class="card card-pad" style="margin-top: 14px">
          <h3 class="side-h">状态说明</h3>
          <p class="muted small" style="line-height: 1.8">{{ statusHint }}</p>
        </div>

        <div class="card card-pad" style="margin-top: 14px" v-if="canCancel">
          <h3 class="side-h">需要取消？</h3>
          <p class="muted small" style="margin-bottom: 10px">审核通过后库存会被冻结，取消会立即释放冻结量。</p>
          <button class="btn btn-block" @click="cancel">取消订单</button>
        </div>
      </div>
    </div>

    <div class="toast" :class="{ err: toastErr }" v-if="toast">{{ toast }}</div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api.js'
import { auth } from '../store.js'
import { ORDER_STATUS, label, statusType, money, qty, dateStr } from '@xinji/shared'

const route = useRoute()
const router = useRouter()
const order = ref(null)
const loading = ref(true)
const toast = ref('')
const toastErr = ref(false)

const steps = [
  { k: 'PENDING_AUDIT', n: '提交订单' },
  { k: 'AUDITED', n: '矿区审核' },
  { k: 'DISPATCHED', n: '派车装货' },
  { k: 'SHIPPED', n: '过磅出厂' },
  { k: 'SIGNED', n: '客户签收' },
  { k: 'SETTLED', n: '对账结算' }
]

const activeStep = computed(() => {
  if (!order.value) return 0
  if (order.value.status === 'CANCELLED') return -1
  const idx = steps.findIndex((s) => s.k === order.value.status)
  if (idx >= 0) return idx
  if (['INVOICED', 'PAID'].includes(order.value.status)) return steps.length - 1
  return 0
})

const canCancel = computed(() => order.value && ['PENDING_AUDIT', 'AUDITED'].includes(order.value.status))

const statusHint = computed(() => {
  if (!order.value) return ''
  const map = {
    PENDING_AUDIT: '订单已提交，等待矿区销售审核。审核通过后会冻结对应库存并安排派车。',
    AUDITED: '审核已通过，库存已冻结。调度正在安排车辆与司机。',
    DISPATCHED: '已派车，司机正在装货，随后到磅房过磅出票。',
    SHIPPED: '货已出厂，磅单净重与扣水已回填，可在订单明细里查看结算吨数。',
    SIGNED: '客户已签收，等待财务按账期生成对账批次。',
    SETTLED: '已进入对账批次并生成应收单，等待回款。',
    INVOICED: '发票已开具。',
    PAID: '款项已结清，订单完成。',
    CANCELLED: '订单已取消，冻结库存已释放。'
  }
  return map[order.value.status] || ''
})

function tip(msg, err) {
  toast.value = msg
  toastErr.value = !!err
  setTimeout(() => (toast.value = ''), 2600)
}

async function load() {
  try {
    order.value = await api.client.order.detail(route.params.orderNo)
  } catch (e) {
    tip(e.message, true)
  } finally {
    loading.value = false
  }
}

async function cancel() {
  if (!confirm('确定取消这笔订单？冻结的库存会立即释放。')) return
  try {
    await api.client.order.cancel(route.params.orderNo, '客户主动取消')
    tip('订单已取消')
    load()
  } catch (e) {
    tip(e.message, true)
  }
}

onMounted(load)
</script>

<style scoped>
.back { font-size: 13px; color: var(--muted); cursor: pointer; margin-bottom: 14px; }
.back:hover { color: var(--ink); }
.head { padding: 22px; margin-bottom: 16px; }
h1 { font-size: 18px; font-weight: 600; letter-spacing: .3px; }
.amount { text-align: right; }
.amount b { display: block; font-size: 26px; color: var(--red); }
.steps { display: flex; gap: 0; margin-top: 22px; padding-top: 20px; border-top: 1px solid var(--line2); }
.step { flex: 1; position: relative; font-size: 12px; color: var(--muted); text-align: center; }
.step .dot {
  width: 9px; height: 9px; border-radius: 50%; background: var(--line);
  margin: 0 auto 8px; position: relative; z-index: 2;
}
.step::before {
  content: ''; position: absolute; top: 4px; left: -50%; width: 100%; height: 1px; background: var(--line);
}
.step:first-child::before { display: none; }
.step.done { color: var(--ink2); }
.step.done .dot { background: var(--brand2); }
.step.done::before { background: var(--brand2); }
.step.cur { color: var(--brand); font-weight: 600; }
.step.cur .dot { background: var(--brand); box-shadow: 0 0 0 4px rgba(27, 79, 108, .12); }
.grid { display: grid; grid-template-columns: 1fr 340px; gap: 16px; align-items: start; }
.foot-sum { padding: 16px 20px; border-top: 1px solid var(--line2); display: flex; flex-direction: column; gap: 8px; align-items: flex-end; }
.foot-sum > div { display: flex; gap: 18px; font-size: 13px; }
.foot-sum .tot { padding-top: 8px; border-top: 1px dashed var(--line); font-size: 14px; }
.foot-sum .tot b { color: var(--red); font-size: 19px; }
.side-h { font-size: 13px; font-weight: 600; margin-bottom: 12px; }
.kv { display: flex; gap: 12px; padding: 6px 0; font-size: 13px; border-bottom: 1px dashed var(--line2); }
.kv:last-child { border-bottom: none; }
.kv span { color: var(--muted); flex: none; width: 66px; }
.kv b { font-weight: 500; }
.kv .addr { line-height: 1.5; }
@media (max-width: 900px) {
  .grid { grid-template-columns: 1fr; }
  .steps { flex-wrap: wrap; gap: 10px; }
  .step { flex: none; width: 30%; }
  .step::before { display: none; }
}
</style>
