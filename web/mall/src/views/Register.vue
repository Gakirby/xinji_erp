<template>
  <div class="reg-wrap">
    <div class="reg-card">
      <div class="lg">鑫</div>
      <h1>注册企业采购账号</h1>
      <p class="muted small">手机号验证 · 注册后即可下单，授信额度由客户经理审核后开通</p>

      <div class="field" style="margin-top: 24px">
        <label>手机号 <i>*</i></label>
        <input
          class="input"
          v-model="form.mobile"
          maxlength="11"
          inputmode="numeric"
          placeholder="用于登录与接收业务通知"
          @blur="checkMobile"
        />
        <div class="hint bad" v-if="mobileHint">{{ mobileHint }}</div>
      </div>

      <div class="field">
        <label>短信验证码 <i>*</i></label>
        <div class="code-row">
          <input class="input" v-model="form.code" maxlength="6" inputmode="numeric" placeholder="6 位数字" />
          <button class="btn btn-sm code-btn" :disabled="counting || sending" @click="sendCode">
            {{ counting ? countdown + 's 后重发' : sending ? '发送中…' : '获取验证码' }}
          </button>
        </div>
        <div class="hint" v-if="devTip">{{ devTip }}</div>
      </div>

      <div class="field">
        <label>企业名称 <i>*</i></label>
        <input class="input" v-model="form.customerName" maxlength="120" placeholder="营业执照上的企业全称" />
      </div>

      <div class="grid2">
        <div class="field">
          <label>联系人</label>
          <input class="input" v-model="form.contactName" maxlength="40" placeholder="选填" />
        </div>
        <div class="field">
          <label>收货地址</label>
          <input class="input" v-model="form.address" maxlength="200" placeholder="选填，下单时可再改" />
        </div>
      </div>

      <div class="field">
        <label>设置密码 <i>*</i></label>
        <input class="input" type="password" v-model="form.password" maxlength="20" placeholder="6-20 位" />
      </div>
      <div class="field">
        <label>确认密码 <i>*</i></label>
        <input class="input" type="password" v-model="form.confirm" maxlength="20" placeholder="再输一次" @keyup.enter="submit" />
      </div>

      <label class="agree">
        <input type="checkbox" v-model="agreed" />
        <span>我已阅读并同意《企业采购服务协议》，承诺所填信息真实有效</span>
      </label>

      <button class="btn btn-primary btn-block" style="margin-top: 10px" :disabled="loading" @click="submit">
        {{ loading ? '注册中…' : '注册并登录' }}
      </button>

      <div class="err" v-if="error">{{ error }}</div>

      <p class="back">
        已有账号？<a @click="$router.push('/login')">返回登录</a>
      </p>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api.js'
import { applyLogin } from '../store.js'

const router = useRouter()

const form = reactive({
  mobile: '',
  code: '',
  customerName: '',
  contactName: '',
  address: '',
  password: '',
  confirm: ''
})
const agreed = ref(true)
const loading = ref(false)
const sending = ref(false)
const error = ref('')
const devTip = ref('')
const mobileHint = ref('')

const counting = ref(false)
const countdown = ref(0)
let timer = null

const MOBILE_RE = /^1[3-9]\d{9}$/

function startCountdown(seconds) {
  counting.value = true
  countdown.value = seconds
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      clearInterval(timer)
      timer = null
      counting.value = false
    }
  }, 1000)
}

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

async function checkMobile() {
  mobileHint.value = ''
  if (!form.mobile) return
  if (!MOBILE_RE.test(form.mobile)) {
    mobileHint.value = '手机号格式不正确'
    return
  }
  try {
    const exists = await api.auth.mobileCheck(form.mobile)
    if (exists) mobileHint.value = '该手机号已注册，可直接登录'
  } catch (e) {
    /* 预校验失败不拦用户，提交时后端还会再校验一次 */
  }
}

async function sendCode() {
  error.value = ''
  devTip.value = ''
  if (!MOBILE_RE.test(form.mobile)) {
    error.value = '请先填写正确的手机号'
    return
  }
  sending.value = true
  try {
    const res = await api.auth.sendSmsCode(form.mobile)
    startCountdown(60)
    if (res.devCode) {
      form.code = res.devCode
      devTip.value = '演示环境：验证码 ' + res.devCode + ' 已自动填入'
    } else {
      devTip.value = res.tip
    }
  } catch (e) {
    error.value = e.message
  } finally {
    sending.value = false
  }
}

async function submit() {
  error.value = ''
  if (!MOBILE_RE.test(form.mobile)) {
    error.value = '请填写正确的手机号'
    return
  }
  if (!/^\d{6}$/.test(form.code)) {
    error.value = '请填写 6 位短信验证码'
    return
  }
  if (!form.customerName.trim()) {
    error.value = '请填写企业名称'
    return
  }
  if (form.password.length < 6) {
    error.value = '密码至少 6 位'
    return
  }
  if (form.password !== form.confirm) {
    error.value = '两次输入的密码不一致'
    return
  }
  if (!agreed.value) {
    error.value = '请先勾选同意服务协议'
    return
  }

  loading.value = true
  try {
    const data = await api.auth.register({
      mobile: form.mobile,
      code: form.code,
      password: form.password,
      customerName: form.customerName.trim(),
      contactName: form.contactName.trim() || undefined,
      address: form.address.trim() || undefined
    })
    applyLogin(data)
    router.replace('/')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.reg-wrap { display: grid; place-items: center; padding: 32px 0 24px; }
.reg-card {
  width: 100%; max-width: 460px; background: #fff; border: 1px solid var(--line);
  border-radius: 14px; padding: 32px 32px 26px; box-shadow: var(--shadow);
}
.lg {
  width: 42px; height: 42px; border-radius: 11px; display: grid; place-items: center;
  background: linear-gradient(140deg, #c07615, #8a4f08); color: #fff; font-size: 19px; font-weight: 700;
  margin-bottom: 14px;
}
h1 { font-size: 20px; font-weight: 600; letter-spacing: .3px; }
.field { margin-bottom: 13px; }
label i { color: var(--red); font-style: normal; }
.code-row { display: flex; gap: 10px; }
.code-row .input { flex: 1; }
.code-btn { white-space: nowrap; height: 38px; min-width: 106px; }
.grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.hint { margin-top: 6px; font-size: 11.5px; color: var(--muted); line-height: 1.5; }
.hint.bad { color: var(--red); }
.agree {
  display: flex; gap: 8px; align-items: flex-start; margin: 16px 0 4px;
  font-size: 12px; color: var(--ink2); line-height: 1.6; cursor: pointer;
}
.agree input { margin-top: 2px; }
.err {
  margin-top: 12px; padding: 9px 12px; border-radius: 7px;
  background: var(--redsoft); color: var(--red); font-size: 12.5px;
}
.back { margin-top: 18px; font-size: 12.5px; color: var(--muted); text-align: center; }
.back a { color: var(--brand2); cursor: pointer; }
@media (max-width: 520px) {
  .reg-card { padding: 24px 18px 20px; }
  .grid2 { grid-template-columns: 1fr; gap: 0; }
}
</style>
