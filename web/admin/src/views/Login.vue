<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <div class="logo">鑫</div>
        <div>
          <b>鑫基矿业 ERP</b>
          <small>OPERATION CONSOLE</small>
        </div>
      </div>

      <h1>运营后台登录</h1>
      <p class="muted small">员工账号登录，客户账号无法进入本系统</p>

      <el-form :model="form" label-position="top" style="margin-top: 22px" @submit.prevent>
        <el-form-item label="登录账号">
          <el-input v-model="form.username" size="large" placeholder="请输入员工账号" @keyup.enter="submit" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" size="large" show-password placeholder="请输入密码" @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="submit">
          登 录
        </el-button>
      </el-form>

      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon style="margin-top: 14px" />

      <div class="demo">
        <span class="muted small">演示账号（点击填充）</span>
        <div class="chips">
          <button v-for="d in demos" :key="d.u" class="chip" @click="fill(d)">{{ d.n }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { api, saveLogin } from '../api.js'

const router = useRouter()
const loading = ref(false)
const error = ref('')
const form = reactive({ username: 'admin', password: 'admin123' })

const demos = [
  { u: 'admin', p: 'admin123', n: 'admin 管理员' },
  { u: 'sales01', p: '123456', n: 'sales01 销售' },
  { u: 'finance01', p: '123456', n: 'finance01 财务' },
  { u: 'weigh01', p: '123456', n: 'weigh01 磅房' }
]

function fill(d) {
  form.username = d.u
  form.password = d.p
}

async function submit() {
  if (!form.username || !form.password) {
    error.value = '请输入账号和密码'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const data = await api.auth.login({ username: form.username, password: form.password })
    if (data.userType !== 'EMPLOYEE') {
      error.value = '这是客户账号，请到商城端登录（/）'
      loading.value = false
      return
    }
    saveLogin(data)
    router.replace('/dashboard')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh; display: grid; place-items: center;
  background: radial-gradient(1200px 600px at 20% 0%, #16394e 0%, #101820 55%, #0c1319 100%);
  padding: 20px;
}
.login-card {
  width: 100%; max-width: 400px; background: #fff; border-radius: 14px;
  padding: 32px 30px 26px; box-shadow: 0 24px 60px -20px rgba(0, 0, 0, .5);
}
.brand { display: flex; align-items: center; gap: 10px; margin-bottom: 22px; }
.logo {
  width: 34px; height: 34px; border-radius: 9px;
  background: linear-gradient(140deg, #c07615, #8a4f08);
  display: grid; place-items: center; color: #fff; font-size: 15px; font-weight: 700;
}
.brand b { display: block; font-size: 13.5px; font-weight: 600; }
.brand small { display: block; font-size: 10px; color: #8b95a1; letter-spacing: .8px; }
h1 { font-size: 19px; font-weight: 600; }
.demo { margin-top: 22px; padding-top: 16px; border-top: 1px dashed var(--el-border-color); }
.chips { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 9px; }
.chip {
  padding: 4px 10px; border-radius: 20px; border: 1px solid var(--el-border-color);
  background: #fff; font-size: 11.5px; color: #49535f;
}
.chip:hover { border-color: var(--el-color-primary); color: var(--el-color-primary); }
</style>
