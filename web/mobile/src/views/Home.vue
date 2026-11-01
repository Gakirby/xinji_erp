<template>
  <div>
    <div class="banner" v-if="activity" @click="$router.push('/seckill')">
      <div class="bt">限时抢购 · {{ activity.activityName }}</div>
      <div class="row" style="margin-top: 6px">
        <h2>低至 7 折</h2>
        <span class="cd">{{ cd.h }}:{{ cd.m }}:{{ cd.s }}</span>
      </div>
      <p class="tiny">活动库存独立计算，抢完即止 · 点击进入会场</p>
    </div>

    <div class="cats">
      <button :class="{ on: cat === '' }" @click="cat = ''">全部</button>
      <button v-for="c in cats" :key="c" :class="{ on: cat === c }" @click="cat = c">{{ c }}</button>
    </div>

    <div v-if="!logged" class="card pad guest">
      <h3>登录后查看报价</h3>
      <p class="small muted">企业采购需核验资质，登录可看实时库存并下单。</p>
      <button class="btn btn-primary btn-block" style="margin-top: 12px" @click="$router.push('/login')">立即登录</button>
    </div>

    <div v-else class="list">
      <div class="loading" v-if="loading">加载中…</div>
      <div class="empty" v-else-if="!filtered.length">暂无物料</div>
      <div class="item" v-for="p in filtered" :key="p.id" @click="$router.push('/product/' + p.id)">
        <div class="thumb" :class="tagClass(p.skuName)">
          <span class="cat">{{ p.category }}</span>
        </div>
        <div class="info">
          <div class="nm">{{ p.skuName }}</div>
          <div class="sp">{{ p.spec }}</div>
          <div class="pr">
            <b>¥{{ money(p.price) }}</b><i>/吨</i>
            <span class="avail" :class="{ warn: Number(p.saleableQty) < Number(p.safetyQty) }">
              可售 {{ qtyShort(p.saleableQty) }}
            </span>
          </div>
          <div class="pools tiny muted">
            总 {{ qtyShort(p.totalQty) }} · 活动 {{ qtyShort(p.activityAllocQty) }} · 冻结 {{ qtyShort(p.frozenQty) }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted, onUnmounted } from 'vue'
import { api, isLogin } from '../api.js'
import { money, qtyShort } from '@xinji/shared'

const products = ref([])
const activity = ref(null)
const loading = ref(false)
const cat = ref('')
const logged = isLogin()
const cd = reactive({ h: '00', m: '00', s: '00' })
let timer = null

const cats = computed(() => {
  const s = new Set(products.value.map((p) => p.category))
  return Array.from(s)
})

const filtered = computed(() => (!cat.value ? products.value : products.value.filter((p) => p.category === cat.value)))

function tagClass(name) {
  const n = name || ''
  if (n.indexOf('机制砂') >= 0) return 'sand'
  if (n.indexOf('铁精粉') >= 0) return 'iron'
  if (n.indexOf('石粉') >= 0) return 'powder'
  if (n.indexOf('尾矿') >= 0) return 'tailing'
  return 'stone'
}

function startCd(end) {
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

onMounted(async () => {
  if (!logged) return
  loading.value = true
  try {
    products.value = (await api.client.product.list()) || []
    const acts = (await api.client.seckill.activities()) || []
    activity.value = acts.find((a) => a.status === 'RUNNING') || acts[0] || null
    if (activity.value) startCd(new Date(String(activity.value.endTime).replace(/-/g, '/')))
  } catch (e) {
    /* ignore */
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.banner {
  background: linear-gradient(115deg, #14384d, #1b4f6c 60%, #22607f);
  border-radius: 12px; padding: 16px; color: #eaf2f7; margin-bottom: 12px;
}
.banner .bt { font-size: 11px; color: #9dc0d3; letter-spacing: .5px; }
.banner h2 { font-size: 20px; font-weight: 600; }
.banner .cd {
  background: rgba(0, 0, 0, .28); padding: 3px 9px; border-radius: 5px;
  font-size: 12px; font-variant-numeric: tabular-nums;
}
.banner p { margin-top: 6px; color: #9db8c8; }

.cats { display: flex; gap: 7px; overflow-x: auto; padding-bottom: 10px; }
.cats button {
  padding: 5px 13px; border-radius: 20px; font-size: 12.5px; white-space: nowrap;
  background: #fff; color: var(--ink2); border: 1px solid var(--line);
}
.cats button.on { background: var(--brand); border-color: var(--brand); color: #fff; }

.guest h3 { font-size: 15px; font-weight: 600; margin-bottom: 6px; }

.list { display: flex; flex-direction: column; gap: 10px; }
.item { display: flex; gap: 11px; background: #fff; border-radius: 10px; padding: 11px; }
.thumb { width: 82px; height: 82px; border-radius: 8px; flex: none; display: grid; place-items: center; }
.cat { background: rgba(0, 0, 0, .22); color: #fff; font-size: 10.5px; padding: 2px 8px; border-radius: 20px; }
.info { flex: 1; min-width: 0; }
.nm { font-size: 14px; font-weight: 600; }
.sp { font-size: 11.5px; color: var(--muted); margin: 2px 0 6px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pr { display: flex; align-items: baseline; gap: 3px; }
.pr b { font-size: 17px; color: var(--red); font-weight: 600; }
.pr i { font-style: normal; font-size: 10.5px; color: var(--muted); }
.avail { margin-left: auto; font-size: 11px; color: var(--green); }
.avail.warn { color: var(--amber); }
.pools { margin-top: 4px; }
.loading { padding: 40px; text-align: center; color: var(--muted); font-size: 13px; }
</style>
