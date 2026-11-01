<template>
  <div class="layout" v-if="!isLoginPage">
    <aside class="side">
      <div class="side-brand">
        <div class="logo">鑫</div>
        <div>
          <b>鑫基矿业 ERP</b>
          <small>运营后台</small>
        </div>
      </div>

      <nav class="side-nav">
        <div v-for="g in menus" :key="g.group" class="group">
          <div class="g-title">{{ g.group }}</div>
          <RouterLink v-for="m in g.items" :key="m.path" :to="m.path" class="nav-item">
            <span>{{ m.name }}</span>
            <span class="dot-badge" v-if="m.path === '/orders' && summary.pendingAudit">{{ summary.pendingAudit }}</span>
          </RouterLink>
        </div>
      </nav>

      <div class="side-foot">
        <span>v1.0.0 · 模块化单体</span>
      </div>
    </aside>

    <div class="main">
      <header class="head">
        <h2>{{ title }}</h2>
        <div class="spacer"></div>
        <span class="env">DEV</span>
        <div class="user">
          <span class="av">{{ initial }}</span>
          <div>
            <b>{{ user.employeeName || user.username }}</b>
            <small>{{ roleLabel }}</small>
          </div>
        </div>
        <button class="logout" @click="onLogout">退出</button>
      </header>

      <div class="content">
        <RouterView />
      </div>
    </div>
  </div>

  <RouterView v-else />
</template>

<script setup>
import { ref, computed, onMounted, provide } from 'vue'
import { RouterView, RouterLink, useRoute } from 'vue-router'
import { currentUser, logout, api } from './api.js'

const route = useRoute()
const user = ref(currentUser() || {})
const summary = ref({})

const menus = [
  { group: '概览', items: [{ path: '/dashboard', name: '经营总览' }] },
  { group: '交易', items: [{ path: '/orders', name: '订单管理' }] },
  { group: '仓储', items: [
    { path: '/stock', name: '库存管理' },
    { path: '/products', name: '物料档案' },
    { path: '/purchase', name: '采购管理' }
  ] },
  { group: '履约', items: [
    { path: '/dispatch', name: '调度派车' },
    { path: '/weigh', name: '过磅出票' },
    { path: '/production', name: '生产管理' }
  ] },
  { group: '财务', items: [
    { path: '/finance', name: '财务结算' },
    { path: '/customers', name: '客户与授信' }
  ] },
  { group: '营销', items: [{ path: '/seckill', name: '秒杀活动' }] },
  { group: '分析', items: [{ path: '/reports', name: '报表中心' }] },
  { group: '系统', items: [{ path: '/users', name: '账号管理' }] }
]

const roleMap = { ADMIN: '管理员', SALES: '销售', FINANCE: '财务', WEIGH: '磅房', DISPATCH: '调度' }

const isLoginPage = computed(() => !!route.meta.public)
const title = computed(() => route.meta.title || '运营后台')
const initial = computed(() => (user.value.employeeName || user.value.username || '管').slice(0, 1))
const roleLabel = computed(() => roleMap[user.value.role] || user.value.role || '员工')

function onLogout() {
  logout()
}

onMounted(async () => {
  if (isLoginPage.value) return
  try {
    summary.value = (await api.admin.dashboard.summary()) || {}
  } catch (e) {
    summary.value = {}
  }
})

provide('refreshBadge', async () => {
  try {
    summary.value = (await api.admin.dashboard.summary()) || {}
  } catch (e) { /* ignore */ }
})
</script>

<style scoped>
.layout { display: flex; min-height: 100vh; }
.side {
  width: 214px; flex: none; background: #101820; color: #cbd5dd;
  display: flex; flex-direction: column; position: sticky; top: 0; height: 100vh;
}
.side-brand {
  display: flex; align-items: center; gap: 10px; padding: 18px 18px 16px;
  border-bottom: 1px solid #1c2833;
}
.logo {
  width: 30px; height: 30px; border-radius: 8px; flex: none;
  background: linear-gradient(140deg, #c07615, #8a4f08);
  display: grid; place-items: center; color: #fff; font-size: 13px; font-weight: 700;
}
.side-brand b { display: block; font-size: 13.5px; color: #eef3f7; font-weight: 600; }
.side-brand small { display: block; font-size: 10.5px; color: #6d7d8b; letter-spacing: .5px; }

.side-nav { flex: 1; overflow-y: auto; padding: 10px 0 20px; }
.group { margin-bottom: 4px; }
.g-title {
  padding: 12px 18px 5px; font-size: 10.5px; color: #5d6b78;
  letter-spacing: .8px; text-transform: uppercase;
}
.nav-item {
  display: flex; align-items: center; padding: 8px 18px; font-size: 13px;
  color: #a9b7c3; transition: .14s; border-left: 2px solid transparent;
}
.nav-item:hover { background: #18232d; color: #fff; }
.nav-item.router-link-active {
  background: #1a2732; color: #fff; border-left-color: #d08a2a; font-weight: 500;
}
.dot-badge {
  margin-left: auto; background: #c0392b; color: #fff; font-size: 10px;
  min-width: 17px; height: 17px; border-radius: 9px; display: grid; place-items: center; padding: 0 5px;
}
.side-foot { padding: 12px 18px; border-top: 1px solid #1c2833; font-size: 10.5px; color: #5d6b78; }

.main { flex: 1; min-width: 0; display: flex; flex-direction: column; background: #f4f6f8; }
.head {
  height: 56px; background: #fff; border-bottom: 1px solid var(--el-border-color-light);
  display: flex; align-items: center; gap: 14px; padding: 0 22px; position: sticky; top: 0; z-index: 30;
}
.head h2 { font-size: 15px; font-weight: 600; }
.spacer { flex: 1; }
.env {
  font-size: 10.5px; color: #0f8a5f; background: #e8f6f0; padding: 3px 8px; border-radius: 4px;
  letter-spacing: .5px;
}
.user { display: flex; align-items: center; gap: 9px; }
.user .av {
  width: 28px; height: 28px; border-radius: 50%; background: #1b4f6c; color: #fff;
  display: grid; place-items: center; font-size: 12px; font-weight: 600;
}
.user b { display: block; font-size: 12.5px; font-weight: 500; }
.user small { display: block; font-size: 10.5px; color: var(--el-text-color-secondary); }
.logout { font-size: 12px; color: var(--el-text-color-secondary); padding: 4px 6px; }
.logout:hover { color: #c0392b; }

.content { padding: 20px 22px 40px; flex: 1; }
</style>
