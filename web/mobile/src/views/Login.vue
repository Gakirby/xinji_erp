<template>
  <div class="login">
    <div class="logo">鑫</div>
    <h1>矿石下单</h1>
    <p class="small muted">吉林鑫基矿业 · 客户登录</p>

    <div class="field" style="margin-top: 24px">
      <label>登录账号 / 手机号</label>
      <input class="input" v-model="username" placeholder="客户账号或注册手机号" />
    </div>
    <div class="field">
      <label>密码</label>
      <input class="input" type="password" v-model="password" placeholder="请输入密码" />
    </div>

    <button class="btn btn-primary btn-block" :disabled="loading" @click="submit">
      {{ loading ? '登录中…' : '登 录' }}
    </button>

    <div class="err small" v-if="error">{{ error }}</div>

    <p class="reg-link">还没有账号？<a @click="$router.push('/register')">手机号注册</a></p>

    <div class="demo">
      <span class="tiny muted">演示账号</span>
      <div class="chips">
        <button v-for="d in demos" :key="d.u" @click="fill(d)">{{ d.n }}</button>
      </div>
    </div>

    <p class="tiny muted" style="margin-top: 18px; text-align: center">
      与网页商城同一账号，购物车和订单互通
    </p>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, saveLogin } from '../api.js'

const router = useRouter()
const route = useRoute()
const username = ref('yatai')
const password = ref('123456')
const loading = ref(false)
const error = ref('')

const demos = [
  { u: 'yatai', p: '123456', n: '长春亚泰建材' },
  { u: 'jianlong', p: '123456', n: '吉林建龙钢铁' }
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
    const data = await api.auth.login({ username: username.value, password: password.value })
    if (data.userType !== 'CUSTOMER') {
      error.value = '这是员工账号，请到运营后台登录'
      return
    }
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
.login { padding: 30px 8px 0; }
.logo {
  width: 44px; height: 44px; border-radius: 12px;
  background: linear-gradient(140deg, #c07615, #8a4f08);
  display: grid; place-items: center; color: #fff; font-size: 20px; font-weight: 700;
  margin-bottom: 14px;
}
h1 { font-size: 21px; font-weight: 600; }
.err { margin-top: 12px; padding: 9px 12px; border-radius: 8px; background: var(--redsoft); color: var(--red); }
.reg-link { margin-top: 14px; font-size: 12.5px; color: var(--muted); text-align: center; }
.reg-link a { color: var(--brand2); cursor: pointer; }
.reg-link a:hover { text-decoration: underline; }
.demo { margin-top: 26px; padding-top: 16px; border-top: 1px dashed var(--line); }
.chips { display: flex; gap: 8px; margin-top: 9px; flex-wrap: wrap; }
.chips button {
  padding: 5px 12px; border-radius: 20px; border: 1px solid var(--line);
  background: #fff; font-size: 12px; color: var(--ink2);
}
</style>
