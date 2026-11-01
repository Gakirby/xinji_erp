<template>
  <div class="phone">
    <header class="mhead">
      <button v-if="showBack" class="back" @click="$router.back()">‹</button>
      <div v-else class="lg">鑫</div>
      <b>{{ title }}</b>
      <div class="r">
        <span v-if="user">{{ user.displayName }}</span>
        <button v-if="!user && !isLoginPage" @click="$router.push('/login')">登录</button>
      </div>
    </header>

    <div class="body">
      <RouterView />
    </div>

    <nav class="tabbar" v-if="!isLoginPage">
      <RouterLink to="/" :class="{ on: $route.path === '/' }">
        <span class="ico"></span><span>选料</span>
      </RouterLink>
      <RouterLink to="/seckill" :class="{ on: $route.path === '/seckill' }">
        <span class="ico"></span><span>抢购</span>
      </RouterLink>
      <RouterLink to="/orders" :class="{ on: $route.path === '/orders' }">
        <span class="ico"></span><span>订单</span>
      </RouterLink>
      <RouterLink to="/mine" :class="{ on: $route.path === '/mine' }">
        <span class="ico"></span><span>我的</span>
      </RouterLink>
    </nav>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { RouterView, RouterLink, useRoute } from 'vue-router'
import { currentUser, isLogin } from './api.js'

const route = useRoute()
const user = ref(currentUser())

const isLoginPage = computed(() => route.name === 'login')
const showBack = computed(() => ['detail', 'orders', 'login'].includes(route.name))
const title = computed(() => {
  const map = { home: '矿石下单', detail: '物料详情', seckill: '限时抢购', orders: '我的订单', mine: '我的', login: '登录' }
  return map[route.name] || '鑫基矿业'
})
</script>
