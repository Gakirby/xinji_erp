<template>
  <div v-if="loading" class="empty">加载中…</div>
  <div v-else-if="!p" class="empty">物料不存在</div>
  <div v-else>
    <div class="hero" :class="tagClass(p.skuName)">
      <span class="cat">{{ p.category }}</span>
    </div>

    <div class="card pad" style="margin-top: 10px">
      <h1>{{ p.skuName }}</h1>
      <p class="small muted" style="margin-top: 4px">{{ p.spec }}</p>
      <div class="price">
        <b>¥{{ money(p.price) }}</b><i>/吨</i>
        <span class="tag" :class="Number(p.saleableQty) >= Number(p.safetyQty) ? 'tag-green' : 'tag-amber'">
          {{ Number(p.saleableQty) >= Number(p.safetyQty) ? '库存充足' : '库存偏低' }}
        </span>
      </div>

      <div class="pools">
        <div><span>物理总量</span><b>{{ qty(p.totalQty) }}</b></div>
        <div><span>销售可售</span><b style="color: var(--green)">{{ qty(p.saleableQty) }}</b></div>
        <div><span>活动划拨</span><b style="color: var(--accent)">{{ qty(p.activityAllocQty) }}</b></div>
        <div><span>订单冻结</span><b style="color: var(--brand2)">{{ qty(p.frozenQty) }}</b></div>
      </div>
    </div>

    <div class="card pad" style="margin-top: 10px">
      <div class="qty-row">
        <span class="small muted">采购吨数</span>
        <div class="qty-ctl">
          <button @click="step(-10)">−</button>
          <input type="number" v-model.number="qtyInput" min="1" />
          <button @click="step(10)">+</button>
          <span class="tiny muted" style="padding-right: 10px">吨</span>
        </div>
      </div>
      <div class="between" style="margin-top: 12px">
        <span class="small muted">预计金额</span>
        <b class="money" style="font-size: 20px; color: var(--red)">¥{{ money(Number(p.price) * Number(qtyInput)) }}</b>
      </div>
      <button class="btn btn-primary btn-block" style="margin-top: 14px" @click="openSheet">立即下单</button>
      <p class="tiny muted" style="text-align: center; margin-top: 8px">提交后矿区审核并派车装货，满 30 吨免运费</p>
    </div>

    <div class="sheet-mask" v-if="show" @click.self="show = false">
      <div class="sheet">
        <h3>确认订单 · {{ p.skuName }}</h3>
        <div class="field"><label>收货地址</label><input class="input" v-model="form.address" /></div>
        <div class="field"><label>收货人</label><input class="input" v-model="form.receiver" /></div>
        <div class="field"><label>期望到货</label><input class="input" v-model="form.expectedDate" placeholder="11-02 08:00 — 12:00" /></div>
        <div class="field">
          <label>配送方式</label>
          <select class="select" v-model="form.deliveryType">
            <option value="DELIVERY">矿区配送</option>
            <option value="PICKUP">客户自提</option>
          </select>
        </div>
        <div class="field"><label>备注</label><textarea class="textarea" v-model="form.remark"></textarea></div>
        <div class="between" style="margin: 6px 0 12px">
          <span class="small muted">{{ qtyShort(qtyInput) }} 吨</span>
          <b class="money" style="color: var(--red); font-size: 18px">¥{{ money(Number(p.price) * Number(qtyInput)) }}</b>
        </div>
        <button class="btn btn-primary btn-block" :disabled="submitting" @click="submit">
          {{ submitting ? '提交中…' : '提交订单' }}
        </button>
      </div>
    </div>

    <div class="toast" :class="{ err: toastErr }" v-if="toast">{{ toast }}</div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, currentUser, isLogin } from '../api.js'
import { money, qty, qtyShort } from '@xinji/shared'

const route = useRoute()
const router = useRouter()
const p = ref(null)
const loading = ref(true)
const qtyInput = ref(30)
const show = ref(false)
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
  qtyInput.value = Math.max(1, Number(qtyInput.value || 0) + d)
}

function openSheet() {
  if (!isLogin()) return router.push('/login')
  const u = currentUser() || {}
  if (!form.address) form.address = u.address || ''
  if (!form.receiver) form.receiver = u.customerName || u.displayName || ''
  if (!form.expectedDate) {
    const d = new Date(Date.now() + 86400000)
    form.expectedDate = `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} 08:00 — 12:00`
  }
  show.value = true
}

async function submit() {
  submitting.value = true
  try {
    const order = await api.client.order.create({
      items: [{ skuId: p.value.id, qty: Number(qtyInput.value) }],
      deliveryType: form.deliveryType,
      address: form.address,
      receiver: form.receiver,
      expectedDate: form.expectedDate,
      remark: form.remark
    })
    show.value = false
    router.push('/orders?new=' + order.order.orderNo)
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
.hero { height: 180px; border-radius: 12px; display: grid; place-items: center; }
.hero .cat { background: rgba(0, 0, 0, .24); color: #fff; padding: 4px 12px; border-radius: 20px; font-size: 11.5px; }
h1 { font-size: 19px; font-weight: 600; }
.price { display: flex; align-items: baseline; gap: 4px; margin: 12px 0; }
.price b { font-size: 26px; color: var(--red); font-weight: 600; }
.price i { font-style: normal; font-size: 11px; color: var(--muted); }
.price .tag { margin-left: auto; }
.pools { display: grid; grid-template-columns: 1fr 1fr; gap: 7px 14px; background: #fafbfc; border-radius: 9px; padding: 12px; }
.pools div { display: flex; justify-content: space-between; font-size: 12px; }
.pools span { color: var(--muted); }
.qty-row { display: flex; align-items: center; justify-content: space-between; }
.qty-ctl { display: flex; align-items: center; border: 1px solid var(--line); border-radius: 9px; overflow: hidden; height: 38px; }
.qty-ctl button { width: 34px; height: 100%; background: #fafbfc; font-size: 16px; }
.qty-ctl input { width: 66px; border: none; text-align: center; outline: none; height: 100%; font-variant-numeric: tabular-nums; }
</style>
