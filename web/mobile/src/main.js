import { createApp } from 'vue'
import App from './App.vue'
import router from './router.js'
import { rehydrate } from './api.js'
import './styles.css'

// 先回填登录态再挂载，避免首屏闪一下未登录
rehydrate().finally(function () {
  createApp(App).use(router).mount('#app')
})
