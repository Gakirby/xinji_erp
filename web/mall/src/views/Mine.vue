<template>
  <div>
    <h1 class="page-title">我的账户</h1>

    <div class="grid">
      <div class="card card-pad">
        <div class="prof">
          <div class="av">{{ initial }}</div>
          <div>
            <h2>{{ user?.displayName || '未登录' }}</h2>
            <p class="muted small">{{ user?.username }} · {{ user?.customerName || '企业客户' }}</p>
          </div>
        </div>

        <h3 class="sub">授信额度</h3>
        <div class="credit">
          <div class="between">
            <span class="muted small">已用 ¥{{ money(user?.creditUsed) }} / 总额 ¥{{ money(user?.creditLimit) }}</span>
            <b class="small" :style="{ color: usedPercent > 80 ? 'var(--red)' : 'var(--green)' }">
              剩余 ¥{{ money(remain) }}
            </b>
          </div>
          <div class="credit-bar">
            <u :style="{ width: Math.min(100, usedPercent) + '%', background: usedPercent > 80 ? 'var(--red)' : 'var(--brand2)' }"></u>
          </div>
          <p class="muted small" style="margin-top: 8px">
            下单金额不得超过剩余授信，超限会被拦截。
          </p>
        </div>
      </div>

      <div class="card card-pad">
        <h3 class="sub" style="margin-top: 0">快捷入口</h3>
        <div class="quick">
          <RouterLink to="/orders" class="q">
            <b>我的订单</b><span>查看状态、明细、结算吨数</span>
          </RouterLink>
          <RouterLink to="/seckill" class="q">
            <b>限时抢购</b><span>活动价矿料，抢完即止</span>
          </RouterLink>
          <RouterLink to="/" class="q">
            <b>选购矿料</b><span>石灰石 / 机制砂 / 铁精粉</span>
          </RouterLink>
        </div>

        <h3 class="sub">我的抢购令牌</h3>
        <div v-if="!tokens.length" class="muted small">暂无待核销令牌</div>
        <div v-else class="tk" v-for="t in tokens.slice(0, 4)" :key="t.id">
          <div>
            <b class="mono small">{{ t.tokenNo }}</b>
            <small class="muted">{{ qtyShort(t.qty) }} 吨 · ¥{{ money(t.price) }}/吨</small>
          </div>
          <span class="tag" :class="'tag-' + statusType(TOKEN_STATUS, t.status)">{{ label(TOKEN_STATUS, t.status) }}</span>
        </div>
      </div>
    </div>

    <div class="card card-pad note">
      <h3 class="sub" style="margin-top: 0">结算与开票</h3>
      <p class="muted small" style="line-height: 1.9">
        签收后由矿区财务按账期生成对账批次，你在订单里看到的「结算吨数」是磅单净重扣水扣杂后的实际结算量。
        对账确认后自动生成应收单，可开 13% 增值税专用发票，回款进度由财务在后台登记，如需对账单请联系业务员。
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '../api.js'
import { auth } from '../store.js'
import { money, qtyShort, label, statusType, TOKEN_STATUS } from '@xinji/shared'

const user = ref(auth.user)
const tokens = ref([])

const initial = computed(() => (user.value?.displayName || '客').slice(0, 1))
const remain = computed(() => Number(user.value?.creditLimit || 0) - Number(user.value?.creditUsed || 0))
const usedPercent = computed(() => {
  const l = Number(user.value?.creditLimit || 0)
  if (!l) return 0
  return (Number(user.value?.creditUsed || 0) / l) * 100
})

onMounted(async () => {
  try {
    const me = await api.auth.me()
    if (me) user.value = { ...(user.value || {}), ...me }
  } catch (e) {
    /* 用本地缓存兜底 */
  }
  try {
    tokens.value = ((await api.client.seckill.myTokens()) || []).filter((t) => t.status === 'ISSUED')
  } catch (e) {
    tokens.value = []
  }
})
</script>

<style scoped>
.page-title { font-size: 20px; font-weight: 600; margin-bottom: 18px; }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.prof { display: flex; align-items: center; gap: 14px; margin-bottom: 24px; }
.av {
  width: 48px; height: 48px; border-radius: 12px; background: var(--brand);
  color: #fff; display: grid; place-items: center; font-size: 19px; font-weight: 600;
}
.prof h2 { font-size: 17px; font-weight: 600; }
.sub { font-size: 13px; font-weight: 600; margin: 18px 0 10px; }
.credit-bar { height: 8px; background: var(--line2); border-radius: 5px; overflow: hidden; margin-top: 8px; }
.credit-bar u { display: block; height: 100%; }
.quick { display: flex; flex-direction: column; gap: 8px; }
.q {
  display: flex; flex-direction: column; padding: 11px 14px; border-radius: 9px;
  border: 1px solid var(--line); transition: .16s;
}
.q:hover { border-color: var(--brand2); background: #fbfcfd; }
.q b { font-size: 13px; font-weight: 600; }
.q span { font-size: 11.5px; color: var(--muted); }
.tk {
  display: flex; align-items: center; justify-content: space-between;
  padding: 9px 0; border-bottom: 1px dashed var(--line2);
}
.tk:last-child { border-bottom: none; }
.tk b { display: block; }
.tk small { display: block; font-size: 11.5px; }
.note { margin-top: 16px; }
@media (max-width: 900px) {
  .grid { grid-template-columns: 1fr; }
}
</style>
