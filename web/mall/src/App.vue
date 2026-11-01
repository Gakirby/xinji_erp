<template>
  <header class="topbar">
    <div class="brand" @click="$router.push('/')">
      <div class="logo">鑫</div>
      <div>
        <b>鑫基矿业 · 企业采购商城</b>
        <small>XINJI MINING MALL</small>
      </div>
    </div>

    <nav class="nav">
      <RouterLink to="/">首页</RouterLink>
      <RouterLink to="/seckill">限时抢购</RouterLink>
      <RouterLink to="/orders">我的订单</RouterLink>
      <RouterLink to="/mine">授信与对账</RouterLink>
    </nav>

    <div class="spacer"></div>

    <div v-if="auth.user" class="who">
      <span class="avatar">{{ initial }}</span>
      <div class="who-t">
        <b>{{ auth.user.displayName }}</b>
        <small>授信额度 ¥{{ money(auth.user.creditLimit) }}</small>
      </div>
      <button class="link" @click="onLogout">退出</button>
    </div>
    <RouterLink v-else class="btn btn-primary" to="/login">登录 / 注册</RouterLink>
  </header>

  <main class="page">
    <RouterView />
  </main>

  <footer class="foot">
    <div class="foot-in">
      <span><b>自有矿山</b>吉林龙潭矿区直供</span>
      <span><b>资质齐全</b>可开 13% 增值税专票</span>
      <span><b>随车磅单</b>净重、扣水、扣杂在线可查</span>
      <span><b>账期支持</b>月结客户在线对账</span>
    </div>
  </footer>
</template>

<script setup>
import { computed } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { money } from '@xinji/shared'
import { auth, logout } from './store.js'

const initial = computed(() => {
  const n = auth.user && auth.user.displayName
  return n ? n.slice(0, 1) : '客'
})

function onLogout() {
  if (confirm('确定退出登录？')) logout()
}
</script>
