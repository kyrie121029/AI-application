import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { clearToken, setOnUnauthorized } from './api/http'
import { useChat } from './composables/useChat'
import './style.css'

// 401（axios 拦截器或 SSE fetch 触发）：清 token、清状态、回登录页
setOnUnauthorized(() => {
  clearToken()
  useChat().reset()
  if (router.currentRoute.value.path !== '/login') {
    router.push('/login')
  }
})

createApp(App).use(router).mount('#app')
