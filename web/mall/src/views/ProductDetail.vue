<template>
  <div v-if="loading" class="loading">加载中…</div>
  <div v-else-if="!p" class="empty">物料不存在或已下架</div>
  <div v-else class="detail">
    <div class="back" @click="$router.back()">← 返回</div>

    <div class="wrap">
      <div class="hero" :class="tagClass(p.skuName)">
        <span class="cat">{{ p.category }}</span>
      </div>

      <div class="info">
        <h1>{{ p.skuName }}</h1>
        <p class="spec">{{ p.spec }}</p>

        <div class="price-box">
          <div class="price"><span>¥</span><b>{{ money(p.price) }}</b><i>/ 吨</i></div>
          <div class="tag tag-green" v-if="Number(p.saleableQty) >= Number(p.safetyQty)">
            库存充足
          </div>
          <div class="tag tag-amber" v-else>库存偏低，建议尽快下单</div>
        </div>

        <div class="pools">
          <div class="pool-row">
            <span>物理总库存</span>
            <b class="mono">{{ qty(p.totalQty) }} 吨</b>
          </div>
          <div class="pool-row">
            <span>销售可售</span>
            <b class="mono" style="color: var(--green)">{{ qty(p.saleableQty) }} 吨</b>
          </div>
          <div class="pool-row">
            <span>活动已划拨</span>
            <b class="mono" style="color: var(--accent)">{{ qty(p.activityAllocQty) }} 吨</b>
          </div>
          <div class="pool-row">
            <span>订单已冻结</span>
            <b class="mono" style="color: var(--brand2)">{{ qty(p.frozenQty) }} 吨</b>
          </div>
          <div class="pool-note">
            总量 = 可售 + 活动划拨 + 订单冻结。下单占用可售量，出厂时才核销。
          </div>
        </div>

        <div class="meta">
          <div><span class="muted">物料编码</span><b class="mono">{{ p.skuNo }}</b></div>
          <div><span class="muted">存放料仓</span><b>{{ p.warehouseNo || '-' }}</b></div>
          <div><span class="muted">计量单位</span><b>{{ p.unit }}</b></div>
          <div><span class="muted">安全库存线</span><b>{{ qtyShort(p.safetyQty) }} 吨</b></div>
        </div>

        <div class="buy-row">
          <div class="qty">
            <label>采购吨数</label>
            <div class="qty-ctl">
              <button @click="step(-10)">−</button>
              <input type="number" v-model.number="buyQty" min="1" />
              <button @click="step(10)">+</button>
              <span class="unit">吨</span>
            </div>
          </div>
          <button class="btn btn-primary" @click="add">加入采购车</button>
          <button class="btn btn-accent" @click="buyNow">立即下单</button>
        </div>
        <p class="hint">单笔满 30 吨免运费 · 下单后由矿区审核并派车装货</p>
      </div>
    </div>

    <div class="modal-mask" v-if="showForm" @click.self="showForm = false">
      <div class="modal">
        <div class="modal-h">
          <h3>确认订单 · {{ p.skuName }}</h3>
          <button class="x" @click="showForm = false">×</button>
        </div>
        <div class="modal-b">
          <div class="field">
            <label>收货地址</label>
            <input class="input" v-model="form.address" />
          </div>
          <div class="row">
            <div class="field" style="flex: 1">
              <label>收货人</label>
              <input class="input" v-model="form.receiver" />
            </div>
            <div class="field" style="flex: 1">
              <label>期望到货时段</label>
              <input class="input" v-model="form.expectedDate" />
            </div>
          </div>
          <div class="field">
            <label>配送方式</label>
            <select class="select" v-model="form.deliveryType">
              <option value="DELIVERY">矿区配送</option>
              <option value="PICKUP">客户自提</option>
            </select>
          </div>
          <div class="field">
            <label>备注</label>
            <textarea class="textarea" v-model="form.remark"></textarea>
          </div>
          <div class="sum">
            <span class="muted">{{ qtyShort(buyQty) }} 吨 × ¥{{ money(p.price) }}</span>
            <b class="money" style="font-size: 18px">¥{{ money(Number(p.price) * Number(buyQty)) }}</b>
          </div>
        </div>
        <div class="modal-f">
          <button class="btn" @click="showForm = false">取消</button>
          <button class="btn btn-primary" :disabled="submitting" @click="submit">
            {{ submitting ? '提交中…' : '提交订单' }}
          </button>
        </div>
      </div>
    </div>

    <div class="toast" :class="{ err: toastErr }" v-if="toast">{{ toast }}</div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api.js'
import { auth, addToCart, isLogin, saveCart } from '../store.js'
import { money, qty, qtyShort } from '@xinji/shared'

const route = useRoute()
const router = useRouter()
const p = ref(null)
const loading = ref(true)
const buyQty = ref(30)
const showForm = ref(false)
const submitting = ref(false)
const toast = ref('')
const toastErr = ref(false)
const form = reactive({ address: '', receiver: '', expectedDate: '', deliveryType: 'DELIVERY', remark: '' })

function tip(msg, err) {
  toast.value = msg
  toastErr.value = !!err
  setTimeout(() => (toast.value = ''), 2600)
}

function tagClass(name) {
  const n = name || ''
  if (n.indexOf('机制砂') >= 0) return 'sand'
  if (n.indexOf('铁精粉') >= 0) return 'iron'
  if (n.indexOf('石粉') >= 0) return 'powder'
  if (n.indexOf('尾矿') >= 0) return 'tailing'
  return 'stone'
}

function step(d) {
  buyQty.value = Math.max(1, Number(buyQty.value || 0) + d)
}

function add() {
  if (!isLogin()) return router.push('/login')
  addToCart(p.value, buyQty.value)
  tip('已加入采购车')
}

function buyNow() {
  if (!isLogin()) return router.push('/login')
  const u = auth.user || {}
  if (!form.address) form.address = u.address || ''
  if (!form.receiver) form.receiver = u.customerName || u.displayName || ''
  if (!form.expectedDate) {
    const d = new Date(Date.now() + 86400000)
    form.expectedDate = `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} 08:00 — 12:00`
  }
  showForm.value = true
}

async function submit() {
  submitting.value = true
  try {
    const order = await api.client.order.create({
      items: [{ skuId: p.value.id, qty: Number(buyQty.value) }],
      deliveryType: form.deliveryType,
      expectedDate: form.expectedDate,
      address: form.address,
      receiver: form.receiver,
      remark: form.remark
    })
    showForm.value = false
    router.push('/orders/' + order.order.orderNo)
  } catch (e) {
    tip(e.message, true)
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    p.value = await api.client.product.detail(route.params.id)
  } catch (e) {
    tip(e.message, true)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.back { font-size: 13px; color: var(--muted); cursor: pointer; margin-bottom: 16px; }
.back:hover { color: var(--ink); }
.wrap { display: grid; grid-template-columns: 420px 1fr; gap: 28px; align-items: start; }
.hero { height: 300px; border-radius: 14px; display: grid; place-items: center; }
.hero .cat { background: rgba(0, 0, 0, .24); color: #fff; padding: 5px 14px; border-radius: 20px; font-size: 12px; }
.stone { background: linear-gradient(135deg, #7d8791, #59636d); }
.sand { background: linear-gradient(135deg, #c9a06a, #a8804b); }
.iron { background: linear-gradient(135deg, #5a6270, #3b424e); }
.powder { background: linear-gradient(135deg, #9aa3ac, #767f88); }
.tailing { background: linear-gradient(135deg, #8b8175, #665e54); }
h1 { font-size: 24px; font-weight: 600; }
.spec { font-size: 13px; color: var(--muted); margin-top: 6px; }
.price-box { display: flex; align-items: center; gap: 14px; margin: 18px 0; }
.price { color: var(--red); }
.price span { font-size: 15px; }
.price b { font-size: 32px; font-weight: 600; }
.price i { font-style: normal; font-size: 12px; color: var(--muted); margin-left: 3px; }
.pools { background: #fafbfc; border: 1px solid var(--line2); border-radius: 10px; padding: 16px 18px; }
.pool-row { display: flex; justify-content: space-between; padding: 5px 0; font-size: 13px; }
.pool-row span { color: var(--ink2); }
.pool-note { margin-top: 8px; padding-top: 10px; border-top: 1px dashed var(--line); font-size: 11.5px; color: var(--muted); }
.meta { display: grid; grid-template-columns: 1fr 1fr; gap: 10px 24px; margin: 18px 0; font-size: 13px; }
.meta div { display: flex; justify-content: space-between; border-bottom: 1px dashed var(--line2); padding-bottom: 6px; }
.buy-row { display: flex; align-items: flex-end; gap: 12px; margin-top: 22px; }
.qty label { display: block; font-size: 12px; color: var(--ink2); margin-bottom: 6px; }
.qty-ctl { display: flex; align-items: center; border: 1px solid var(--line); border-radius: 8px; overflow: hidden; height: 38px; }
.qty-ctl button { width: 34px; height: 100%; background: #fafbfc; font-size: 15px; color: var(--ink2); }
.qty-ctl input { width: 78px; height: 100%; border: none; text-align: center; outline: none; font-variant-numeric: tabular-nums; }
.qty-ctl .unit { padding: 0 12px 0 4px; font-size: 12px; color: var(--muted); }
.hint { margin-top: 12px; font-size: 11.5px; color: var(--muted); }
.sum { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px; background: #fafbfc; border-radius: 8px; }
@media (max-width: 900px) {
  .wrap { grid-template-columns: 1fr; }
  .hero { height: 200px; }
  .buy-row { flex-wrap: wrap; }
}
</style>
