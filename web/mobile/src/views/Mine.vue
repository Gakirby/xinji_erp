<template>
  <div>
    <div class="card pad">
      <div class="prof">
        <div class="av">{{ initial }}</div>
        <div>
          <b>{{ user?.displayName || '未登录' }}</b>
          <small class="muted tiny">{{ user?.username }} · 企业客户</small>
        </div>
      </div>

      <div class="credit">
        <div class="between small">
          <span class="muted">授信额度</span>
          <b>¥{{ money(user?.creditLimit) }}</b>
        </div>
        <div class="bar"><u :style="{ width: used + '%' }"></u></div>
        <div class="between tiny muted" style="margin-top: 6px">
          <span>已用 ¥{{ money(user?.creditUsed) }}</span>
          <span>剩余 ¥{{ money(remain) }}</span>
        </div>
      </div>
    </div>

    <div class="card" style="margin-top: 10px">
      <div class="cell" @click="$router.push('/orders')">
        <span>我的订单</span><span class="arrow">›</span>
      </div>
      <div class="cell" @click="$router.push('/seckill')">
        <span>抢购记录</span><span class="arrow">›</span>
      </div>
      <div class="cell" @click="$router.push('/')">
        <span>继续选料</span><span class="arrow">›</span>
      </div>
    </div>

    <div class="card pad" style="margin-top: 10px">
      <div class="sec-t">待核销令牌</div>
      <div v-if="!tokens.length" class="tiny muted">暂无</div>
      <div class="tk" v-for="t in tokens" :key="t.id">
        <div>
          <b class="mono tiny">{{ t.tokenNo }}</b>
          <small class="muted tiny">{{ qtyShort(t.qty) }} 吨 · ¥{{ money(t.price) }}/吨</small>
        </div>
        <span class="tag tag-amber">待核销</span>
      </div>
    </div>

    <button class="btn btn-block" style="margin-top: 14px" @click="onLogout">退出登录</button>
    <p class="tiny muted" style="text-align: center; margin-top: 14px">
      结算吨数以磅单净重扣水扣杂为准，可在订单明细查看
    </p>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { api, currentUser, logout } from '../api.js'
import { money, qtyShort } from '@xinji/shared'

const user = ref(currentUser())
const tokens = ref([])

const initial = computed(() => (user.value?.displayName || '客').slice(0, 1))
const remain = computed(() => Number(user.value?.creditLimit || 0) - Number(user.value?.creditUsed || 0))
const used = computed(() => {
  const l = Number(user.value?.creditLimit || 0)
  if (!l) return 0
  return Math.min(100, (Number(user.value?.creditUsed || 0) / l) * 100)
})

function onLogout() {
  if (confirm('确定退出登录？')) logout()
}

onMounted(async () => {
  try {
    const me = await api.auth.me()
    if (me) user.value = { ...(user.value || {}), ...me }
  } catch (e) { /* ignore */ }
  try {
    tokens.value = ((await api.client.seckill.myTokens()) || []).filter((t) => t.status === 'ISSUED')
  } catch (e) { /* ignore */ }
})
</script>

<style scoped>
.prof { display: flex; align-items: center; gap: 12px; margin-bottom: 18px; }
.av {
  width: 44px; height: 44px; border-radius: 12px; background: var(--brand); color: #fff;
  display: grid; place-items: center; font-size: 18px; font-weight: 600;
}
.prof b { font-size: 16px; }
.prof small { display: block; }
.credit { background: #fafbfc; border-radius: 9px; padding: 12px; }
.bar { height: 7px; background: #e7ebee; border-radius: 4px; overflow: hidden; margin-top: 8px; }
.bar u { display: block; height: 100%; background: var(--brand2); }
.cell {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 16px; border-bottom: 1px solid var(--line2); font-size: 14px;
}
.cell:last-child { border-bottom: none; }
.arrow { color: var(--muted); font-size: 16px; }
.sec-t { font-size: 13px; font-weight: 600; margin-bottom: 8px; }
.tk { display: flex; align-items: center; justify-content: space-between; padding: 8px 0; border-bottom: 1px dashed var(--line2); }
.tk:last-child { border-bottom: none; }
.tk b { display: block; }
.tk small { display: block; }
</style>
