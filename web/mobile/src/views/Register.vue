<template>
  <div class="reg">
    <div class="logo">鑫</div>
    <h1>注册采购账号</h1>
    <p class="tiny muted">手机号验证 · 注册后即可下单</p>

    <div class="field" style="margin-top: 20px">
      <label>手机号 <i>*</i></label>
      <input class="input" v-model="form.mobile" maxlength="11" inputmode="numeric" placeholder="用于登录与接单通知" />
    </div>

    <div class="field">
      <label>短信验证码 <i>*</i></label>
      <div class="code-row">
        <input class="input" v-model="form.code" maxlength="6" inputmode="numeric" placeholder="6 位数字" />
        <button class="code-btn" :disabled="counting || sending" @click="sendCode">
          {{ counting ? countdown + 's' : sending ? '…' : '获取验证码' }}
        </button>
      </div>
      <div class="tiny muted" style="margin-top: 6px" v-if="devTip">{{ devTip }}</div>
    </div>

    <div class="field">
      <label>企业名称 <i>*</i></label>
      <input class="input" v-model="form.customerName" maxlength="120" placeholder="营业执照企业全称" />
    </div>

    <div class="field">
      <label>设置密码 <i>*</i></label>
      <input class="input" type="password" v-model="form.password" maxlength="20" placeholder="6-20 位" />
    </div>
    <div class="field">
      <label>确认密码 <i>*</i></label>
      <input class="input" type="password" v-model="form.confirm" maxlength="20" placeholder="再输一次" @keyup.enter="submit" />
    </div>

    <button class="btn btn-primary btn-block" :disabled="loading" @click="submit">
      {{ loading ? '注册中…' : '注册并登录' }}
    </button>

    <div class="err small" v-if="error">{{ error }}</div>

    <p class="tiny muted" style="margin-top: 18px; text-align: center">
      已有账号？<a class="link" @click="$router.push('/login')">返回登录</a>
    </p>
  </div>
</template>

<script setup>
import { reactive, ref, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, saveLogin } from '../api.js'

const router = useRouter()
const route = useRoute()

const form = reactive({
  mobile: '',
  code: '',
  customerName: '',
  password: '',
  confirm: ''
})
const loading = ref(false)
const sending = ref(false)
const error = ref('')
const devTip = ref('')
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

  loading.value = true
  try {
    const data = await api.auth.register({
      mobile: form.mobile,
      code: form.code,
      password: form.password,
      customerName: form.customerName.trim()
    })
    saveLogin(data)
    router.replace(route.query.redirect || '/')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.reg { padding: 30px 8px 0; }
.logo {
  width: 44px; height: 44px; border-radius: 12px;
  background: linear-gradient(140deg, #c07615, #8a4f08);
  display: grid; place-items: center; color: #fff; font-size: 20px; font-weight: 700;
  margin-bottom: 14px;
}
h1 { font-size: 21px; font-weight: 600; }
.field { margin-bottom: 12px; }
label i { color: var(--red); font-style: normal; }
.code-row { display: flex; gap: 8px; }
.code-row .input { flex: 1; }
.code-btn {
  white-space: nowrap; min-width: 96px; height: 38px;
  border: 1px solid var(--line); border-radius: 8px; background: #fff;
  font-size: 12.5px; color: var(--brand);
}
.code-btn:disabled { opacity: .5; }
.err { margin-top: 12px; padding: 9px 12px; border-radius: 8px; background: var(--redsoft); color: var(--red); }
.link { color: var(--brand2); cursor: pointer; }
</style>
