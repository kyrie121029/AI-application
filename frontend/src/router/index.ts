import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '../api/http'
import LoginView from '../views/LoginView.vue'
import ChatView from '../views/ChatView.vue'
import FilesView from '../views/FilesView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView },
    { path: '/', name: 'chat', component: ChatView },
    { path: '/files', name: 'files', component: FilesView },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

// 守卫：无 token → /login；已登录访问 /login → /
router.beforeEach((to) => {
  const authed = !!getToken()
  if (!authed && to.name !== 'login') return { name: 'login' }
  if (authed && to.name === 'login') return { name: 'chat' }
  return true
})

export default router
