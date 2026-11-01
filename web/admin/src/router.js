import { createRouter, createWebHistory } from 'vue-router'
import { isLogin } from './api.js'

const routes = [
  { path: '/login', name: 'login', component: () => import('./views/Login.vue'), meta: { public: true } },
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'dashboard', component: () => import('./views/Dashboard.vue'), meta: { title: '经营总览' } },
  { path: '/orders', name: 'orders', component: () => import('./views/Orders.vue'), meta: { title: '订单管理' } },
  { path: '/stock', name: 'stock', component: () => import('./views/Stock.vue'), meta: { title: '库存管理' } },
  { path: '/products', name: 'products', component: () => import('./views/Products.vue'), meta: { title: '物料档案' } },
  { path: '/purchase', name: 'purchase', component: () => import('./views/Purchase.vue'), meta: { title: '采购管理' } },
  { path: '/dispatch', name: 'dispatch', component: () => import('./views/Dispatch.vue'), meta: { title: '调度派车' } },
  { path: '/weigh', name: 'weigh', component: () => import('./views/Weigh.vue'), meta: { title: '过磅出票' } },
  { path: '/production', name: 'production', component: () => import('./views/Production.vue'), meta: { title: '生产管理' } },
  { path: '/customers', name: 'customers', component: () => import('./views/Customers.vue'), meta: { title: '客户与授信' } },
  { path: '/finance', name: 'finance', component: () => import('./views/Finance.vue'), meta: { title: '财务结算' } },
  { path: '/seckill', name: 'seckill', component: () => import('./views/Seckill.vue'), meta: { title: '秒杀活动' } },
  { path: '/reports', name: 'reports', component: () => import('./views/Reports.vue'), meta: { title: '报表中心' } },
  { path: '/users', name: 'users', component: () => import('./views/Users.vue'), meta: { title: '账号管理' } },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory('/admin/'),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

router.beforeEach((to, from, next) => {
  if (!to.meta.public && !isLogin()) {
    next('/login')
    return
  }
  next()
})

export default router
