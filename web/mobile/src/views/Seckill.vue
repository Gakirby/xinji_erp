<template>
  <div>
    <div class="head" v-if="activity">
      <div class="t">
        <b>{{ activity.activityName }}</b>
        <small>{{ statusText(activity.status) }}</small>
      </div>
      <div class="cd">{{ cd.h }}:{{ cd.m }}:{{ cd.s }}</div>
    </div>

    <div class="acts" v-if="activities.length > 1">
      <button
        v-for="a in activities"
        :key="a.id"
        :class="{ on: activity && a.id === activity.id }"
        @click="pick(a)"
      >{{ a.activityName }}</button>
    </div>

    <div class="loading" v-if="loading">加载中…</div>
    <div class="empty" v-else-if="!skus.length">活动还没上架商品</div>

    <div class="sk" v-for="s in skus" :key="s.id">
      <div class="between">
        <b>{{ s.skuName }}</b>
        <span class="tag" :class="s.status === 'ON' && Number(s.remainQty) > 0 ? 'tag-red' : 'tag-gray'">
          {{ s.status === 'ON' && Number(s.remainQty) > 0 ? '抢购中' : '已抢光' }}
        </span>
      </div>
      <div class="pr">
        <b>¥{{ money(s.seckillPrice) }}</b><i>/吨</i>
        <del>¥{{ money(s.originalPrice) }}</del>
      </div>
      <div class="bar"><u :style="{ width: soldPct(s) }"></u></div>
      <div class="rows">
        <span>剩余 <b style="color: var(--red)">{{ qtyShort(s.remainQty) }}</b> 吨</span>
        <span>限购 {{ qtyShort(s.limitPerUser) }} 吨</span>
      </div>
      <div class="grab">
        <input type="number" v-model.number="grabQty[s.id]" min="1" />
        <button class="btn btn-accent" :disabled="busy === s.id || s.status !== 'ON'" @click="grab(s)">
          {{ busy === s.id ? '抢购中…' : '立即抢' }}
        </button>
      </div>
    </div>

    <div class="sec">我的令牌</div>
    <div class="card">
      <div class="empty" v-if="!tokens.length" style="padding: 30px">还没有抢购记录</div>
      <div class="tk" v-for="t in tokens" :key="t.id">
        <div>
          <b class="mono tiny">{{ t.tokenNo }}</b>
          <small class="muted tiny">{{ qtyShort(t.qty) }} 吨 · ¥{{ money(t.price) }}</small>
        </div>
        <span class="tag" :class="'tag-' + statusType(TOKEN_STATUS, t.status)">{{ label(TOKEN_STATUS, t.status) }}</span>
      </div>
    </div>

    <div class="sheet-mask" v-if="pending" @click.self="pending = null">
      <div class="sheet">
        <h3>抢到了 · {{ qtyShort(pending.qty) }} 吨</h3>
        <p class="small muted" style="margin-bottom: 12px">
          令牌 {{ pending.tokenNo }}，15 分钟内下单有效
        </p>
        <div class="field"><label>收货地址</label><input class="input" v-model="form.address" /></div>
        <div class="field"><label>收货人</label><input class="input" v-model="form.receiver" /></div>
        <div class="field"><label>期望到货</label><input class="input" v-model="form.expectedDate" /></div>
        <div class="between" style="margin-bottom: 12px">
          <span class="small muted">成交金额</span>
          <b class="money" style="color: var(--red); font-size: 18px">
            ¥{{ money(Number(pending.qty) * Number(pending.price)) }}
          </b>
        </div>
        <button class="btn btn-primary btn-block" :disabled="submitting" @click="useToken">
          {{ submitting ? '提交中…' : '用令牌下单' }}
        </button>
      </div>
    </div>

    <div class="toast" :class="{ err: toastErr }" v-if="toast">{{ toast }}</div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { api, currentUser, isLogin } from '../api.js'
import { money, qtyShort, label, statusType, TOKEN_STATUS } from '@xinji/shared'

const router = useRouter()
const activities = ref([])
const activity = ref(null)
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
const form = reactive({ address: '', receiver: '', expectedDate: '' })
let timer = null

function tip(msg, err) {
  toast.value = msg
  toastErr.value = !!err
  setTimeout(() => (toast.value = ''), 2600)
}

function statusText(s) {
  return { DRAFT: '草稿', PUBLISHED: '即将开始', RUNNING: '进行中', ENDED: '已结束' }[s] || s
}

function soldPct(s) {
  const t = Number(s.activityStock || 0)
  if (!t) return '0%'
  return Math.min(100, (Number(s.soldQty || 0) / t) * 100).toFixed(1) + '%'
}

function pick(a) {
  activity.value = a
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

async function loadSkus() {
  loading.value = true
  try {
    skus.value = (await api.client.seckill.skus(activity.value.id)) || []
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
  } catch (e) { /* ignore */ }
}

async function grab(s) {
  if (!isLogin()) return router.push('/login')
  busy.value = s.id
  try {
    const r = await api.client.seckill.grab({ seckillSkuId: s.id, qty: Number(grabQty[s.id] || 0) })
    if (r && r.success) {
      tip('抢购成功，正在发券')
      await loadSkus()
      poll(r.tokenNo)
    } else {
      tip((r && r.reason) || '没抢到', true)
      await loadSkus()
    }
  } catch (e) {
    tip(e.message, true)
  } finally {
    busy.value = 0
  }
}

async function poll(tokenNo) {
  for (let i = 0; i < 12; i++) {
    await new Promise((r) => setTimeout(r, 500))
    try {
      const t = await api.client.seckill.token(tokenNo)
      if (t && t.status === 'ISSUED') {
        const u = currentUser() || {}
        form.address = u.address || ''
        form.receiver = u.customerName || u.displayName || ''
        form.expectedDate = '明早 08:00 — 12:00'
        pending.value = t
        loadTokens()
        return
      }
      if (t && ['FAILED', 'EXPIRED'].includes(t.status)) {
        tip('令牌发放失败：' + (t.failReason || t.status), true)
        return
      }
    } catch (e) { /* 继续 */ }
  }
  tip('令牌仍在发放，稍后在下方查看', true)
}

async function useToken() {
  submitting.value = true
  try {
    const order = await api.client.seckill.useToken(pending.value.tokenNo, {
      deliveryType: 'DELIVERY',
      address: form.address,
      receiver: form.receiver,
      expectedDate: form.expectedDate
    })
    pending.value = null
    router.push('/orders?new=' + order.order.orderNo)
  } catch (e) {
    tip(e.message, true)
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  loadTokens()
  try {
    activities.value = (await api.client.seckill.activities()) || []
    const run = activities.value.find((a) => a.status === 'RUNNING') || activities.value[0]
    if (run) pick(run)
  } catch (e) {
    tip(e.message, true)
  }
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.head {
  background: linear-gradient(115deg, #3d1b13, #7a2f1c 60%, #a04122);
  border-radius: 12px; padding: 14px 16px; color: #fdf1ea;
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px;
}
.head b { display: block; font-size: 15px; }
.head small { font-size: 11px; color: #e5b3a0; }
.head .cd { background: rgba(0, 0, 0, .32); padding: 4px 10px; border-radius: 6px; font-size: 12px; font-variant-numeric: tabular-nums; }

.acts { display: flex; gap: 7px; overflow-x: auto; margin-bottom: 10px; }
.acts button { padding: 5px 12px; border-radius: 20px; background: #fff; border: 1px solid var(--line); font-size: 12px; white-space: nowrap; }
.acts button.on { border-color: var(--accent); background: var(--accentsoft); color: var(--accent); }

.sk { background: #fff; border-radius: 11px; padding: 14px; margin-bottom: 10px; }
.sk b { font-size: 14.5px; }
.pr { display: flex; align-items: baseline; gap: 6px; margin: 8px 0; }
.pr b { font-size: 22px; color: var(--red); font-weight: 600; }
.pr i { font-style: normal; font-size: 10.5px; color: var(--muted); }
.pr del { font-size: 11.5px; color: var(--muted); }
.bar { height: 6px; background: var(--line2); border-radius: 4px; overflow: hidden; }
.bar u { display: block; height: 100%; background: linear-gradient(90deg, #d98c2b, #bd7415); }
.rows { display: flex; justify-content: space-between; font-size: 11.5px; color: var(--muted); margin: 6px 0 10px; }
.grab { display: flex; gap: 8px; }
.grab input { width: 78px; height: 40px; border: 1px solid var(--line); border-radius: 9px; text-align: center; font-variant-numeric: tabular-nums; }
.grab button { flex: 1; }

.sec { font-size: 13px; font-weight: 600; margin: 16px 0 8px; }
.tk { display: flex; align-items: center; justify-content: space-between; padding: 11px 14px; border-bottom: 1px solid var(--line2); }
.tk:last-child { border-bottom: none; }
.tk b { display: block; }
.tk small { display: block; }
.loading { padding: 40px; text-align: center; color: var(--muted); font-size: 13px; }
</style>
