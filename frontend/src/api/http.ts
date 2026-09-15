import axios, { type AxiosError } from 'axios'
import type { ApiResponse } from '../types'

// ==================== JWT 存取 ====================

const TOKEN_KEY = 'ai_app_token'
const USERNAME_KEY = 'ai_app_username'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function getUsername(): string | null {
  return localStorage.getItem(USERNAME_KEY)
}

export function setUsername(username: string): void {
  localStorage.setItem(USERNAME_KEY, username)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USERNAME_KEY)
}

// ==================== 401 全局处理（由 main.ts 注册，避免与 router 循环依赖） ====================

let onUnauthorized: () => void = () => {}

export function setOnUnauthorized(fn: () => void): void {
  onUnauthorized = fn
}

export function handleUnauthorized(): void {
  onUnauthorized()
}

// ==================== axios 实例：自动携带 JWT ====================

export const http = axios.create({
  baseURL: '/api',
  timeout: 30_000,
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (resp) => resp,
  (error: AxiosError<ApiResponse<unknown>>) => {
    if (error.response?.status === 401) {
      handleUnauthorized()
    }
    return Promise.reject(error)
  },
)

/** 从 axios 错误中提取后端 ApiResponse.message（拿不到就用兜底文案） */
export function errorMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data
    if (data?.message) return data.message
    if (err.response?.status) return `请求失败 (HTTP ${err.response.status})`
  }
  return fallback
}
