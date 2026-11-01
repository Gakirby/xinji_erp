<template>
  <div class="home">
    <div class="banner" v-if="topActivity">
      <div>
        <div class="bt">LIMITED · 矿料专场</div>
        <h2>限时抢购 <em>低至 7.2 折</em></h2>
        <p>{{ topActivity.activityName }} · 活动库存与销售库存分开管理，抢完即止</p>
        <div class="row" style="margin-top: 18px">
          <div class="cd">
            <span>距结束</span>
            <b>{{ cd.h }}</b><b>{{ cd.m }}</b><b>{{ cd.s }}</b>
          </div>
          <button class="btn btn-accent" @click="$router.push('/seckill')">进入会场</button>
        </div>
      </div>
      <div class="banner-side">
        <div class="side-item"><b>自有矿山直供</b><span>资质齐全，可开 13% 专票</span></div>
        <div class="side-item"><b>满 30 吨免运费</b><span>当日装车，次日送达</span></div>
        <div class="side-item"><b>随车地磅单</b><span>净重与扣水在线可查</span></div>
      </div>
    </div>

    <div v-if="!logged" class="card card-pad guest">
      <h3>登录后查看矿产品报价</h3>
      <p class="muted small">企业采购需要先核验资质，登录后可以查看实时库存、提交订单、抢购活动料。</p>
      <div class="row" style="margin-top: 16px">
        <RouterLink class="btn btn-primary" to="/login">立即登录</RouterLink>
        <span class="muted small">演示账号：yatai / 123456</span>
      </div>
    </div>

    <template v-else>
      <div class="sk-row" v-if="seckillSkus.length">
        <div class="sk-c" v-for="s in seckillSkus.slice(0, 4)" :key="s.id" @click="$router.push('/seckill')">
          <div class="sk-img" :class="tagClass(s.skuName)">
            <span v-if="s.remainQty > 0">剩 {{ qtyShort(s.remainQty) }} 吨</span>
            <span v-else class="sold">已售罄</span>
          </div>
          <div class="sk-bd">
            <div class="nm">{{ s.skuName }}</div>
            <div class="pr">
              <b>¥{{ money(s.seckillPrice) }}<i>/吨</i></b>
              <del>¥{{ money(s.originalPrice) }}</del>
            </div>
            <div class="bar"><u :style="{ width: soldPercent(s) }"></u></div>
            <div class="ft">
              <span>活动 {{ qtyShort(s.activityStock) }} 吨</span>
              <span>已抢 {{ qtyShort(s.soldQty) }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="layout">
        <div class="main">
          <div class="sec-h">
            <h2>全部矿产品</h2>
            <span class="more">共 {{ products.length }} 个在售品种 · 可售量 = 总量 − 活动划拨 − 订单冻结</span>
          </div>

          <div class="loading" v-if="loading">加载中…</div>
          <div class="empty" v-else-if="!products.length">暂无在售物料</div>

          <div class="grid" v-else>
            <div class="wp" v-for="p in products" :key="p.id">
              <div class="wp-img" :class="tagClass(p.skuName)">
                <span class="cat">{{ p.category }}</span>
              </div>
              <div class="wp-bd">
                <div class="nm" @click="$router.push('/product/' + p.id)">{{ p.skuName }}</div>
                <div class="sp">{{ p.spec }}</div>
                <div class="pr">
                  <b>¥{{ money(p.price) }}</b><i>/吨</i>
                  <em :class="{ warn: Number(p.saleableQty) < Number(p.safetyQty) }">
                    可售 {{ qtyShort(p.saleableQty) }}
                  </em>
                </div>
                <div class="pool">
                  <span>总量 {{ qtyShort(p.totalQty) }}</span>
                  <span>活动 {{ qtyShort(p.activityAllocQty) }}</span>
                  <span>冻结 {{ qtyShort(p.frozenQty) }}</span>
                </div>
                <button class="btn btn-block" @click="openBuy(p)">加入采购车</button>
              </div>
            </div>
          </div>
        </div>

        <aside class="cart">
          <div class="card-h">
            <h3>采购车</h3>
            <span class="right muted small">{{ auth.cart.length }} 项 · {{ qtyShort(cartQty()) }} 吨</span>
          </div>
          <div v-if="!auth.cart.length" class="empty" style="padding: 44px 16px">
            还没有选料<br /><span class="small">从左侧点「加入采购车」</span>
          </div>
          <div v-else class="cart-list">
            <div class="ci" v-for="c in auth.cart" :key="c.skuId">
              <div class="ci-th" :class="tagClass(c.skuName)"></div>
              <div class="ci-b">
                <b>{{ c.skuName }}</b>
                <small>{{ qtyShort(c.qty) }} 吨 × ¥{{ money(c.price) }}</small>
                <div class="ci-op">
                  <input class="qty-in" type="number" min="1" v-model.number="c.qty" @change="saveCart()" />
                  <button class="link" @click="removeFromCart(c.skuId)">移除</button>
                </div>
              </div>
              <div class="ci-r money">¥{{ money(Number(c.price) * Number(c.qty)) }}</div>
            </div>
          </div>
          <div class="cart-foot" v-if="auth.cart.length">
            <div class="between"><span class="muted">商品金额</span><b class="money">¥{{ money(cartTotal()) }}</b></div>
            <div class="between"><span class="muted">运费</span><b style="color: var(--green)">免运费</b></div>
            <div class="between tot"><span>应付合计</span><b class="money">¥{{ money(cartTotal()) }}</b></div>
            <button class="btn btn-primary btn-block" @click="openCheckout">去结算</button>
            <div class="hint">提交后锁定库存，等待矿区审核装车</div>
          </div>
        </aside>
      </div>
    </template>

    <div class="modal-mask" v-if="showCheckout" @click.self="showCheckout = false">
      <div class="modal">
        <div class="modal-h">
          <h3>确认订单</h3>
          <button class="x" @click="showCheckout = false">×</button>
        </div>
        <div class="modal-b">
          <div class="field">
            <label>收货地址</label>
            <input class="input" v-model="form.address" placeholder="省 / 市 / 区 + 详细地址" />
          </div>
          <div class="row">
            <div class="field" style="flex: 1">
              <label>收货人</label>
              <input class="input" v-model="form.receiver" placeholder="姓名 / 联系方式" />
            </div>
            <div class="field" style="flex: 1">
              <label>期望到货时段</label>
              <input class="input" v-model="form.expectedDate" placeholder="11-02 08:00 — 12:00" />
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
            <textarea class="textarea" v-model="form.remark" placeholder="如：需随车磅单原件、要求夜间卸货"></textarea>
          </div>
          <div class="sum">
            <span class="muted">合计 {{ qtyShort(cartQty()) }} 吨</span>
            <b class="money" style="font-size: 18px">¥{{ money(cartTotal()) }}</b>
          </div>
        </div>
        <div class="modal-f">
          <button class="btn" @click="showCheckout = false">再想想</button>
          <button class="btn btn-primary" :disabled="submitting" @click="submit">
            {{ submitting ? '提交中…' : '提交订单' }}
          </button>
        </div>
      </div>
    </div>

    <transition name="fade">
      <div class="toast" :class="{ err: toastErr }" v-if="toast">{{ toast }}</div>
    </transition>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api.js'
import { auth, addToCart, removeFromCart, saveCart, cartTotal, cartQty, isLogin } from '../store.js'
import { money, qtyShort } from '@xinji/shared'

const router = useRouter()
const logged = isLogin()
const products = ref([])
const seckillSkus = ref([])
const topActivity = ref(null)
const loading = ref(false)
const showCheckout = ref(false)
const submitting = ref(false)
const toast = ref('')
const toastErr = ref(false)
const cd = reactive({ h: '00', m: '00', s: '00' })

const form = reactive({
  address: '',
  receiver: '',
  expectedDate: '',
  deliveryType: 'DELIVERY',
  remark: ''
})

let timer = null

function tip(msg, isErr) {
  toast.value = msg
  toastErr.value = !!isErr
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

function soldPercent(s) {
  const total = Number(s.activityStock || 0)
  if (!total) return '0%'
  return Math.min(100, (Number(s.soldQty || 0) / total) * 100).toFixed(1) + '%'
}

async function load() {
  loading.value = true
  try {
    products.value = (await api.client.product.list()) || []
  } catch (e) {
    tip(e.message, true)
  } finally {
    loading.value = false
  }
}

async function loadSeckill() {
  try {
    const acts = (await api.client.seckill.activities()) || []
    const run = acts.find((a) => a.status === 'RUNNING') || acts[0]
    if (!run) return
    topActivity.value = run
    seckillSkus.value = (await api.client.seckill.skus(run.id)) || []
    startCountdown(new Date(String(run.endTime).replace(/-/g, '/')))
  } catch (e) {
    /* 活动为空不影响首页 */
  }
}

function startCountdown(end) {
  const tick = () => {
    const diff = end.getTime() - Date.now()
    if (diff <= 0) {
      cd.h = cd.m = cd.s = '00'
      return
    }
    const h = Math.floor(diff / 3600000)
    const m = Math.floor((diff % 3600000) / 60000)
    const s = Math.floor((diff % 60000) / 1000)
    const p = (n) => String(n).padStart(2, '0')
    cd.h = p(h)
    cd.m = p(m)
    cd.s = p(s)
  }
  tick()
  timer = setInterval(tick, 1000)
}

function openBuy(p) {
  if (!isLogin()) {
    router.push('/login')
    return
  }
  addToCart(p, 10)
  tip('已加入采购车：' + p.skuName)
}

function openCheckout() {
  const u = auth.user || {}
  if (!form.address) form.address = u.address || ''
  if (!form.receiver) form.receiver = u.customerName || u.displayName || ''
  if (!form.expectedDate) {
    const d = new Date(Date.now() + 86400000)
    form.expectedDate = `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} 08:00 — 12:00`
  }
  showCheckout.value = true
}

async function submit() {
  if (!form.address || !form.receiver) {
    tip('请填写收货地址和收货人', true)
    return
  }
  submitting.value = true
  try {
    const order = await api.client.order.create({
      items: auth.cart.map((c) => ({ skuId: c.skuId, qty: Number(c.qty) })),
      deliveryType: form.deliveryType,
      expectedDate: form.expectedDate,
      address: form.address,
      receiver: form.receiver,
      remark: form.remark
    })
    auth.cart = []
    saveCart()
    showCheckout.value = false
    tip('下单成功，等待矿区审核')
    router.push('/orders/' + order.order.orderNo)
  } catch (e) {
    tip(e.message, true)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (!logged) return
  load()
  loadSeckill()
  const u = auth.user || {}
  form.address = u.address || ''
  form.receiver = u.customerName || u.displayName || ''
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.banner {
  display: grid; grid-template-columns: 1fr 300px; gap: 0;
  background: linear-gradient(115deg, #14384d 0%, #1b4f6c 55%, #22607f 100%);
  border-radius: 14px; overflow: hidden; color: #eaf2f7;
}
.banner > div:first-child { padding: 34px 36px; }
.banner .bt { font-size: 11px; letter-spacing: 1.4px; color: #9dc0d3; margin-bottom: 8px; }
.banner h2 { font-size: 27px; font-weight: 600; letter-spacing: .4px; }
.banner h2 em { font-style: normal; color: #f0b64e; }
.banner p { font-size: 13px; color: #b9d0dd; margin-top: 8px; }
.cd { display: flex; align-items: center; gap: 5px; font-size: 12px; color: #b9d0dd; }
.cd b {
  background: rgba(0, 0, 0, .28); color: #fff; padding: 4px 8px; border-radius: 5px;
  font-variant-numeric: tabular-nums; font-weight: 500;
}
.banner-side { background: rgba(0, 0, 0, .16); padding: 22px 24px; display: flex; flex-direction: column; gap: 16px; }
.side-item b { display: block; font-size: 13px; font-weight: 600; color: #eaf2f7; }
.side-item span { font-size: 12px; color: #9db8c8; }

.guest { margin-top: 22px; }
.guest h3 { font-size: 15px; font-weight: 600; margin-bottom: 6px; }

.sk-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-top: 20px; }
.sk-c {
  background: #fff; border: 1px solid var(--line); border-radius: 10px; overflow: hidden;
  cursor: pointer; transition: .18s;
}
.sk-c:hover { transform: translateY(-2px); box-shadow: var(--shadow); }
.sk-img { height: 84px; position: relative; display: grid; place-items: center; color: #fff; font-size: 12px; }
.sk-img.sold span { opacity: .9; }
.sk-bd { padding: 12px 14px 14px; }
.sk-bd .nm { font-size: 13.5px; font-weight: 600; }
.sk-bd .pr { display: flex; align-items: baseline; gap: 7px; margin: 6px 0 8px; }
.sk-bd .pr b { color: var(--red); font-size: 17px; font-weight: 600; }
.sk-bd .pr b i { font-style: normal; font-size: 11px; font-weight: 400; }
.sk-bd .pr del { color: var(--muted); font-size: 11.5px; }
.bar { height: 5px; background: var(--line2); border-radius: 4px; overflow: hidden; }
.bar u { display: block; height: 100%; background: linear-gradient(90deg, #d98c2b, #bd7415); }
.sk-bd .ft { display: flex; justify-content: space-between; font-size: 11.5px; color: var(--muted); margin-top: 6px; }

.stone { background: linear-gradient(135deg, #7d8791, #59636d); }
.sand { background: linear-gradient(135deg, #c9a06a, #a8804b); }
.iron { background: linear-gradient(135deg, #5a6270, #3b424e); }
.powder { background: linear-gradient(135deg, #9aa3ac, #767f88); }
.tailing { background: linear-gradient(135deg, #8b8175, #665e54); }

.layout { display: grid; grid-template-columns: 1fr 330px; gap: 22px; align-items: start; }
.grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.wp { background: #fff; border: 1px solid var(--line); border-radius: 10px; overflow: hidden; }
.wp-img { height: 110px; position: relative; display: grid; place-items: center; }
.wp-img .cat {
  font-size: 11px; color: #fff; background: rgba(0, 0, 0, .22); padding: 3px 9px; border-radius: 20px;
}
.wp-bd { padding: 14px; }
.wp-bd .nm { font-size: 13.5px; font-weight: 600; cursor: pointer; }
.wp-bd .nm:hover { color: var(--brand2); }
.wp-bd .sp { font-size: 11.5px; color: var(--muted); margin: 4px 0 8px; }
.wp-bd .pr { display: flex; align-items: baseline; gap: 4px; margin-bottom: 8px; }
.wp-bd .pr b { font-size: 18px; color: var(--ink); font-weight: 600; }
.wp-bd .pr i { font-style: normal; font-size: 11px; color: var(--muted); }
.wp-bd .pr em { margin-left: auto; font-style: normal; font-size: 11.5px; color: var(--green); }
.wp-bd .pr em.warn { color: var(--amber); }
.pool { display: flex; gap: 10px; font-size: 11px; color: var(--muted); margin-bottom: 12px; }

.cart { position: sticky; top: 78px; background: #fff; border: 1px solid var(--line); border-radius: 10px; overflow: hidden; }
.cart-list { max-height: 420px; overflow-y: auto; }
.ci { display: flex; gap: 10px; padding: 12px 16px; border-bottom: 1px solid var(--line2); align-items: flex-start; }
.ci-th { width: 40px; height: 40px; border-radius: 7px; flex: none; }
.ci-b { flex: 1; min-width: 0; }
.ci-b b { display: block; font-size: 12.5px; font-weight: 600; }
.ci-b small { display: block; font-size: 11px; color: var(--muted); }
.ci-op { display: flex; align-items: center; gap: 10px; margin-top: 5px; }
.qty-in { width: 62px; height: 26px; border: 1px solid var(--line); border-radius: 6px; padding: 0 6px; font-size: 12px; }
.ci-r { font-size: 12.5px; color: var(--ink); white-space: nowrap; }
.cart-foot { padding: 16px; border-top: 1px solid var(--line); display: flex; flex-direction: column; gap: 8px; }
.cart-foot .tot { padding-top: 8px; border-top: 1px dashed var(--line); font-size: 14px; }
.cart-foot .tot b { color: var(--red); font-size: 19px; }
.cart-foot .hint { font-size: 11px; color: var(--muted); text-align: center; }

.sum { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px; background: #fafbfc; border-radius: 8px; }
.fade-enter-active, .fade-leave-active { transition: opacity .2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

@media (max-width: 1080px) {
  .layout { grid-template-columns: 1fr; }
  .cart { position: static; }
  .sk-row { grid-template-columns: repeat(2, 1fr); }
  .grid { grid-template-columns: repeat(2, 1fr); }
  .banner { grid-template-columns: 1fr; }
  .banner-side { display: none; }
}
@media (max-width: 640px) {
  .grid { grid-template-columns: 1fr; }
}
</style>
