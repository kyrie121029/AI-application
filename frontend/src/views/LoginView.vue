<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { errorMessage, http, setToken, setUsername } from '../api/http'
import { useChat } from '../composables/useChat'

const router = useRouter()
const chat = useChat()

const mode = ref<'login' | 'register'>('login')
const form = reactive({ username: '', password: '' })
const submitting = ref(false)
const error = ref<string | null>(null)

async function submit(): Promise<void> {
  if (submitting.value) return
  const username = form.username.trim()
  if (!username || !form.password) {
    error.value = '请输入用户名和密码'
    return
  }
  submitting.value = true
  error.value = null
  try {
    if (mode.value === 'register') {
      // 注册成功后自动登录
      await http.post('/auth/register', { username, password: form.password })
    }
    const resp = await http.post('/auth/login', { username, password: form.password })
    setToken(resp.data.data.token)
    setUsername(resp.data.data.username)
    chat.reset()
    router.push('/')
  } catch (err) {
    error.value = errorMessage(err, mode.value === 'login' ? '登录失败' : '注册失败')
  } finally {
    submitting.value = false
  }
}

function switchMode(next: 'login' | 'register'): void {
  mode.value = next
  error.value = null
}
</script>

<template>
  <div class="login-page">
    <div class="login-panel">
      <p class="mono login-eyebrow">AI-APPLICATION</p>
      <h1 class="login-title">{{ mode === 'login' ? '登录工作台' : '注册账号' }}</h1>

      <form @submit.prevent="submit">
        <div class="login-field">
          <input
            v-model="form.username"
            class="field"
            type="text"
            name="username"
            autocomplete="username"
            placeholder="用户名"
          />
        </div>
        <div class="login-field">
          <input
            v-model="form.password"
            class="field"
            type="password"
            name="password"
            autocomplete="current-password"
            placeholder="密码"
          />
        </div>
        <p class="error-line login-error" role="alert">{{ error }}</p>
        <button class="btn-primary login-submit" type="submit" :disabled="submitting">
          {{ submitting ? '请稍候…' : mode === 'login' ? '登录' : '注册并登录' }}
        </button>
      </form>

      <p class="login-switch">
        {{ mode === 'login' ? '还没有账号？' : '已有账号？' }}
        <button type="button" @click="switchMode(mode === 'login' ? 'register' : 'login')">
          {{ mode === 'login' ? '注册一个' : '去登录' }}
        </button>
      </p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.login-panel {
  width: 100%;
  max-width: 380px;
  background: var(--surface);
  border: var(--hairline);
  border-radius: 14px;
  padding: 32px;
}

.login-eyebrow {
  color: var(--faint);
  margin: 0 0 8px;
}

.login-title {
  margin: 0 0 24px;
  font-size: 22px;
  font-weight: 700;
}

.login-field {
  margin-bottom: 14px;
}

.login-error {
  margin: 0 0 12px;
  min-height: 20px;
}

.login-submit {
  width: 100%;
}

.login-switch {
  margin: 18px 0 0;
  text-align: center;
  font-size: 13px;
  color: var(--muted);
}

.login-switch button {
  color: var(--accent);
  font-weight: 500;
}
</style>
