/** 后端统一响应体 ApiResponse<T>：{code, message, data} */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/** GET /api/chat 返回的会话 */
export interface Conversation {
  id: number
  title: string
  createdAt: string
}

/** GET /api/chat/{id}/messages 返回的消息（role 为后端小写化后的值） */
export interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  createdAt: string
}

/** 聊天请求体（新建会话可带 title） */
export interface ChatRequestPayload {
  title?: string
  content: string
  requestId?: string
}

export interface LoginResponse {
  token: string
  username: string
}

/** SSE event: error 的 data 负载 */
export interface SseErrorPayload {
  errorType: string
  message: string
  retryable: boolean
}
