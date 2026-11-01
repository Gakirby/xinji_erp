import { createRouter, createWebHistory } from 'vue-router'
import { isLogin } from './api.js'

const routes = [
  { path: '/', name: 'home', component: () => import('./views/Home.vue') },
  { path: '/login', name: 'login', component: () => import('./views/Login.vue') },
  { path: '/register', name: 'register', component: () => import('./views/Register.vue') },
  { path: '/product/:id', name: 'detail', component: () => import('./views/Detail.vue') },
  { path: '/seckill', name: 'seckill', component: () => import('./views/Seckill.vue') },
  { path: '/orders', name: 'orders', component: () => import('./views/Orders.vue'), meta: { auth: true } },
  { path: '/mine', name: 'mine', component: () => import('./views/Mine.vue'), meta: { auth: true } },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory('/m/'),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

router.beforeEach((to, from, next) => {
  if (to.meta.auth && !isLogin()) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  next()
})

export default router
