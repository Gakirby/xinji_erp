<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="lg">鑫</div>
      <h1>企业采购商城</h1>
      <p class="muted small">吉林鑫基矿业有限公司 · 客户登录</p>

      <div class="field" style="margin-top: 26px">
        <label>登录账号</label>
        <input class="input" v-model="username" placeholder="请输入客户账号" @keyup.enter="submit" />
      </div>
      <div class="field">
        <label>密码</label>
        <input class="input" type="password" v-model="password" placeholder="请输入密码" @keyup.enter="submit" />
      </div>

      <button class="btn btn-primary btn-block" style="margin-top: 8px" :disabled="loading" @click="submit">
        {{ loading ? '登录中…' : '登 录' }}
      </button>

      <div class="err" v-if="error">{{ error }}</div>

      <div class="demo">
        <span class="muted small">演示客户账号</span>
        <div class="demo-list">
          <button class="chip" v-for="d in demos" :key="d.u" @click="fill(d)">{{ d.n }}</button>
        </div>
      </div>

      <p class="tip">同一账号也可登录微信小程序端，购物车与订单互通。</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../store.js'

const router = useRouter()
const route = useRoute()
const username = ref('yatai')
const password = ref('123456')
const loading = ref(false)
const error = ref('')

const demos = [
  { u: 'yatai', p: '123456', n: '长春亚泰建材' },
  { u: 'jianlong', p: '123456', n: '吉林建龙钢铁' },
  { u: 'yatai_sn', p: '123456', n: '吉林亚泰水泥' }
]

function fill(d) {
  username.value = d.u
  password.value = d.p
}

async function submit() {
  if (!username.value || !password.value) {
    error.value = '请输入账号和密码'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await login(username.value, password.value)
    const redirect = route.query.redirect
    router.replace(typeof redirect === 'string' ? redirect : '/')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap { display: grid; place-items: center; padding: 40px 0 20px; }
.login-card {
  width: 100%; max-width: 400px; background: #fff; border: 1px solid var(--line);
  border-radius: 14px; padding: 34px 32px 28px; box-shadow: var(--shadow);
}
.lg {
  width: 42px; height: 42px; border-radius: 11px; display: grid; place-items: center;
  background: linear-gradient(140deg, #c07615, #8a4f08); color: #fff; font-size: 19px; font-weight: 700;
  margin-bottom: 14px;
}
h1 { font-size: 20px; font-weight: 600; letter-spacing: .3px; }
.field { margin-bottom: 14px; }
.err {
  margin-top: 12px; padding: 9px 12px; border-radius: 7px;
  background: var(--redsoft); color: var(--red); font-size: 12.5px;
}
.demo { margin-top: 24px; padding-top: 18px; border-top: 1px dashed var(--line); }
.demo-list { display: flex; gap: 8px; margin-top: 10px; flex-wrap: wrap; }
.chip {
  padding: 5px 11px; border-radius: 20px; border: 1px solid var(--line);
  font-size: 12px; color: var(--ink2); background: #fff;
}
.chip:hover { border-color: var(--brand2); color: var(--brand); }
.tip { margin-top: 18px; font-size: 11.5px; color: var(--muted); line-height: 1.6; }
</style>
