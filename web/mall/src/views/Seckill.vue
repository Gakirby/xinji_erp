<template>
  <div>
    <div class="hero">
      <div>
        <div class="bt">LIMITED TIME</div>
        <h1>矿料限时抢购</h1>
        <p>活动库存独立于销售库存，Redis Lua 原子扣减，抢到的是提货凭证，15 分钟内下单有效</p>
      </div>
      <div class="cd" v-if="current">
        <span>距结束</span>
        <b>{{ cd.h }}</b><b>{{ cd.m }}</b><b>{{ cd.s }}</b>
      </div>
    </div>

    <div class="acts" v-if="activities.length">
      <button
        v-for="a in activities"
        :key="a.id"
        class="act"
        :class="{ on: current && current.id === a.id }"
        @click="select(a)"
      >
        <b>{{ a.activityName }}</b>
        <small>{{ a.activityNo }} · {{ activityStatus(a.status) }}</small>
      </button>
    </div>

    <div class="loading" v-if="loading">加载中…</div>
    <div class="empty" v-else-if="!skus.length">当前活动还没有上架商品</div>

    <div class="sk-grid" v-else>
      <div class="sk" v-for="s in skus" :key="s.id" :class="{ off: s.status !== 'ON' || Number(s.remainQty) <= 0 }">
        <div class="sk-top">
          <span class="badge" v-if="s.status === 'SOLDOUT' || Number(s.remainQty) <= 0">已抢光</span>
          <span class="badge hot" v-else-if="Number(s.remainQty) < 100">即将抢完</span>
          <span class="badge" v-else>{{ s.seckillSkuNo }}</span>
        </div>
        <h3>{{ s.skuName }}</h3>
        <div class="price">
          <b>¥{{ money(s.seckillPrice) }}</b><i>/吨</i>
          <del>¥{{ money(s.originalPrice) }}</del>
          <span class="save">省 {{ discount(s) }}%</span>
        </div>
        <div class="bar"><u :style="{ width: soldPercent(s) }"></u></div>
        <div class="rows">
          <div><span>活动库存</span><b>{{ qtyShort(s.activityStock) }} 吨</b></div>
          <div><span>已抢</span><b>{{ qtyShort(s.soldQty) }} 吨</b></div>
          <div><span>剩余</span><b style="color: var(--red)">{{ qtyShort(s.remainQty) }} 吨</b></div>
          <div><span>每人限购</span><b>{{ qtyShort(s.limitPerUser) }} 吨</b></div>
        </div>
        <div class="grab-row">
          <input type="number" class="qty" v-model.number="grabQty[s.id]" min="1" :disabled="s.status !== 'ON'" />
          <button class="btn btn-accent btn-block" :disabled="busy === s.id || s.status !== 'ON'" @click="grab(s)">
            {{ busy === s.id ? '抢购中…' : (s.status !== 'ON' ? '已售罄' : '立即抢购') }}
          </button>
        </div>
        <p class="note">销售可售池另计 {{ qtyShort(s.saleableQty) }} 吨，不参与本次活动</p>
      </div>
    </div>

    <div class="sec-h">
      <h2>我的抢购记录</h2>
      <span class="more">抢到后 15 分钟内下单，超时库存自动回池</span>
    </div>

    <div class="card">
      <div class="empty" v-if="!tokens.length" style="padding: 40px">
        还没有抢购记录
      </div>
      <table class="table" v-else>
        <thead>
          <tr>
            <th>令牌号</th>
            <th>吨数</th>
            <th class="num">成交价</th>
            <th>状态</th>
            <th>过期时间</th>
            <th style="width: 130px"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in tokens" :key="t.id">
            <td class="mono small">{{ t.tokenNo }}</td>
            <td class="mono">{{ qtyShort(t.qty) }} 吨</td>
            <td class="num money">¥{{ money(t.price) }}</td>
            <td>
              <span class="tag" :class="'tag-' + statusType(TOKEN_STATUS, t.status)">
                {{ label(TOKEN_STATUS, t.status) }}
              </span>
              <span v-if="t.failReason" class="muted small" style="margin-left: 6px">{{ t.failReason }}</span>
            </td>
            <td class="muted small">{{ dateStr(t.expireTime) }}</td>
            <td style="text-align: right">
              <button v-if="t.status === 'ISSUED'" class="btn btn-sm btn-primary" @click="useToken(t)">去下单</button>
              <RouterLink v-else-if="t.orderNo" class="btn btn-sm" :to="'/orders/' + t.orderNo">查看订单</RouterLink>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="modal-mask" v-if="pending" @click.self="pending = null">
      <div class="modal">
        <div class="modal-h"><h3>抢购成功 · 确认提货信息</h3><button class="x" @click="pending = null">×</button></div>
        <div class="modal-b">
          <div class="ok-box">
            <div class="ok">令牌 {{ pending.tokenNo }}</div>
            <p class="small muted">
              {{ qtyShort(pending.qty) }} 吨 × ¥{{ money(pending.price) }} =
              <b style="color: var(--red)">¥{{ money(Number(pending.qty) * Number(pending.price)) }}</b>
            </p>
          </div>
          <div class="field"><label>收货地址</label><input class="input" v-model="form.address" /></div>
          <div class="row">
            <div class="field" style="flex: 1"><label>收货人</label><input class="input" v-model="form.receiver" /></div>
            <div class="field" style="flex: 1"><label>期望到货</label><input class="input" v-model="form.expectedDate" /></div>
          </div>
          <div class="field"><label>备注</label><textarea class="textarea" v-model="form.remark"></textarea></div>
        </div>
        <div class="modal-f">
          <button class="btn" @click="pending = null">稍后下单</button>
          <button class="btn btn-primary" :disabled="submitting" @click="submitToken">
            {{ submitting ? '提交中…' : '用令牌下单' }}
          </button>
        </div>
      </div>
    </div>

    <div class="toast" :class="{ err: toastErr }" v-if="toast">{{ toast }}</div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { api } from '../api.js'
import { auth, isLogin } from '../store.js'
import { money, qtyShort, dateStr, label, statusType, TOKEN_STATUS } from '@xinji/shared'

const router = useRouter()
const activities = ref([])
const current = ref(null)
const skus = ref([])
const tokens = ref([])
const loading = ref(false)
const busy = ref(0)
const submitting = ref(false)
const pending = ref(null)
const grabQty = reactive({})
const cd = reactive({ h: '00', m: '00', s: '00' })
const toast = ref('')
const toastErr = ref(false)
const form = reactive({ address: '', receiver: '', expectedDate: '', remark: '' })
let timer = null

function tip(msg, err) {
  toast.value = msg
  toastErr.value = !!err
  setTimeout(() => (toast.value = ''), 2800)
}

function activityStatus(s) {
  const map = { DRAFT: '草稿', PUBLISHED: '已发布', RUNNING: '进行中', ENDED: '已结束' }
  return map[s] || s
}

function soldPercent(s) {
  const total = Number(s.activityStock || 0)
  if (!total) return '0%'
  return Math.min(100, (Number(s.soldQty || 0) / total) * 100).toFixed(1) + '%'
}

function discount(s) {
  const o = Number(s.originalPrice || 0)
  if (!o) return '0'
  return (((o - Number(s.seckillPrice || 0)) / o) * 100).toFixed(0)
}

function select(a) {
  current.value = a
  loadSkus()
  startCd(new Date(String(a.endTime).replace(/-/g, '/')))
}

function startCd(end) {
  if (timer) clearInterval(timer)
  const tick = () => {
    const diff = end.getTime() - Date.now()
    if (diff <= 0) { cd.h = cd.m = cd.s = '00'; return }
    const p = (n) => String(n).padStart(2, '0')
    cd.h = p(Math.floor(diff / 3600000))
    cd.m = p(Math.floor((diff % 3600000) / 60000))
    cd.s = p(Math.floor((diff % 60000) / 1000))
  }
  tick()
  timer = setInterval(tick, 1000)
}

async function loadActivities() {
  try {
    activities.value = (await api.client.seckill.activities()) || []
    const run = activities.value.find((a) => a.status === 'RUNNING') || activities.value[0]
    if (run) select(run)
  } catch (e) {
    tip(e.message, true)
  }
}

async function loadSkus() {
  if (!current.value) return
  loading.value = true
  try {
    skus.value = (await api.client.seckill.skus(current.value.id)) || []
    skus.value.forEach((s) => { if (!grabQty[s.id]) grabQty[s.id] = 30 })
  } catch (e) {
    skus.value = []
  } finally {
    loading.value = false
  }
}

async function loadTokens() {
  if (!isLogin()) return
  try {
    tokens.value = (await api.client.seckill.myTokens()) || []
  } catch (e) {
    tokens.value = []
  }
}

async function grab(s) {
  if (!isLogin()) return router.push('/login')
  busy.value = s.id
  try {
    const r = await api.client.seckill.grab({ seckillSkuId: s.id, qty: Number(grabQty[s.id] || 0) })
    if (r && r.success) {
      tip('抢购成功，正在发券…')
      await loadSkus()
      await pollToken(r.tokenNo)
    } else {
      tip((r && r.reason) || '没抢到，换个吨数试试', true)
      await loadSkus()
    }
  } catch (e) {
    tip(e.message, true)
  } finally {
    busy.value = 0
  }
}

async function pollToken(tokenNo) {
  for (let i = 0; i < 12; i++) {
    await new Promise((r) => setTimeout(r, 500))
    try {
      const t = await api.client.seckill.token(tokenNo)
      if (t && t.status === 'ISSUED') {
        pending.value = t
        return
      }
      if (t && (t.status === 'FAILED' || t.status === 'EXPIRED')) {
        tip('令牌发放失败：' + (t.failReason || t.status), true)
        return
      }
    } catch (e) {
      /* 继续轮询 */
    }
  }
  tip('令牌仍在发放中，稍后可在下方记录里查看', true)
}

function useToken(t) {
  pending.value = t
  const u = auth.user || {}
  form.address = u.address || ''
  form.receiver = u.customerName || u.displayName || ''
}

async function submitToken() {
  submitting.value = true
  try {
    const order = await api.client.seckill.useToken(pending.value.tokenNo, {
      deliveryType: 'DELIVERY',
      address: form.address,
      receiver: form.receiver,
      expectedDate: form.expectedDate,
      remark: form.remark
    })
    pending.value = null
    tip('下单成功')
    router.push('/orders/' + order.order.orderNo)
  } catch (e) {
    tip(e.message, true)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadActivities()
  loadTokens()
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.hero {
  background: linear-gradient(115deg, #3d1b13, #7a2f1c 60%, #a04122);
  border-radius: 14px; padding: 30px 34px; color: #fdf1ea;
  display: flex; align-items: center; justify-content: space-between; gap: 20px;
}
.hero .bt { font-size: 11px; letter-spacing: 1.4px; color: #e5b3a0; }
.hero h1 { font-size: 26px; font-weight: 600; margin: 6px 0; }
.hero p { font-size: 12.5px; color: #f0c4b2; max-width: 640px; }
.cd { display: flex; align-items: center; gap: 5px; font-size: 12px; color: #f0c4b2; flex: none; }
.cd b { background: rgba(0, 0, 0, .32); color: #fff; padding: 5px 9px; border-radius: 5px; font-variant-numeric: tabular-nums; }

.acts { display: flex; gap: 10px; margin: 18px 0 16px; flex-wrap: wrap; }
.act {
  padding: 10px 16px; border-radius: 9px; border: 1px solid var(--line); background: #fff;
  text-align: left;
}
.act b { display: block; font-size: 13px; font-weight: 600; }
.act small { display: block; font-size: 11px; color: var(--muted); }
.act.on { border-color: var(--accent); background: var(--accentsoft); }

.sk-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.sk { background: #fff; border: 1px solid var(--line); border-radius: 12px; padding: 18px; }
.sk.off { opacity: .66; }
.sk-top { display: flex; justify-content: space-between; }
.badge {
  font-size: 11px; padding: 3px 9px; border-radius: 20px;
  background: var(--line2); color: var(--ink2);
}
.badge.hot { background: var(--redsoft); color: var(--red); }
.sk h3 { font-size: 15px; font-weight: 600; margin: 10px 0 8px; }
.price { display: flex; align-items: baseline; gap: 8px; }
.price b { font-size: 24px; color: var(--red); font-weight: 600; }
.price i { font-style: normal; font-size: 11px; color: var(--muted); }
.price del { font-size: 12px; color: var(--muted); }
.price .save { font-size: 11px; color: var(--accent); background: var(--accentsoft); padding: 2px 7px; border-radius: 4px; }
.bar { height: 6px; background: var(--line2); border-radius: 4px; overflow: hidden; margin: 12px 0; }
.bar u { display: block; height: 100%; background: linear-gradient(90deg, #d98c2b, #bd7415); }
.rows { display: grid; grid-template-columns: 1fr 1fr; gap: 6px 14px; font-size: 12px; }
.rows div { display: flex; justify-content: space-between; }
.rows span { color: var(--muted); }
.grab-row { display: flex; gap: 8px; margin-top: 14px; }
.qty {
  width: 86px; height: 36px; border: 1px solid var(--line); border-radius: 8px;
  padding: 0 10px; text-align: center; font-variant-numeric: tabular-nums;
}
.note { margin-top: 10px; font-size: 11px; color: var(--muted); }

.ok-box { background: var(--greensoft); border-radius: 9px; padding: 14px 16px; }
.ok { font-size: 14px; font-weight: 600; color: var(--green); font-family: ui-monospace, monospace; }

@media (max-width: 1080px) {
  .sk-grid { grid-template-columns: repeat(2, 1fr); }
  .hero { flex-direction: column; align-items: flex-start; }
}
@media (max-width: 640px) {
  .sk-grid { grid-template-columns: 1fr; }
}
</style>
