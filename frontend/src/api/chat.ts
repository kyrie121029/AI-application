import { http, getToken, handleUnauthorized } from './http'
import type {
  ApiResponse,
  ChatMessage,
  ChatRequestPayload,
  Conversation,
  SseErrorPayload,
} from '../types'

// ==================== 会话 CRUD（axios，自动带 JWT） ====================

export async function listConversations(): Promise<Conversation[]> {
  const resp = await http.get<ApiResponse<Conversation[]>>('/chat')
  return resp.data.data
}

export async function listMessages(conversationId: number): Promise<ChatMessage[]> {
  const resp = await http.get<ApiResponse<ChatMessage[]>>(`/chat/${conversationId}/messages`)
  return resp.data.data
}

export async function deleteConversation(conversationId: number): Promise<void> {
  await http.delete<ApiResponse<void>>(`/chat/${conversationId}`)
}

// ==================== SSE 流式聊天（fetch + ReadableStream + TextDecoder + AbortController） ====================
// 不使用原生 EventSource：接口是 POST 且需要 Authorization Header。
// 必须维护 buffer：一次 reader.read() 可能只包含半个事件，也可能包含多个事件。

export interface StreamHandlers {
  onChunk: (text: string) => void
  /** 可选：流正常结束时回调（新建会话时历史由 listMessages 接管，不需要） */
  onDone?: () => void
  onSseError: (payload: SseErrorPayload) => void
}

export type StreamOutcome =
  | { kind: 'ok' }
  | { kind: 'http_error'; status: number; message: string }
  | { kind: 'aborted' }

export async function streamChat(
  url: string,
  payload: ChatRequestPayload,
  signal: AbortSignal,
  handlers: StreamHandlers,
): Promise<StreamOutcome> {
  const token = getToken()

  let resp: Response
  try {
    resp = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(payload),
      signal,
    })
  } catch (err) {
    if (isAbortError(err)) return { kind: 'aborted' }
    return { kind: 'http_error', status: 0, message: '网络连接失败' }
  }

  // 非 2xx：发生在 SSE 建立之前（400/401/403/404/409/5xx），错误信息在后端 ApiResponse JSON 里
  if (!resp.ok || !resp.body) {
    if (resp.status === 401) handleUnauthorized()
    return { kind: 'http_error', status: resp.status, message: await extractHttpError(resp) }
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      // 以空行分隔事件；残余 buffer 留到下一轮，直到读到空行或流结束
      let sep: number
      while ((sep = buffer.indexOf('\n\n')) !== -1) {
        const rawEvent = buffer.slice(0, sep)
        buffer = buffer.slice(sep + 2)
        dispatchSseEvent(rawEvent, handlers)
      }
    }
    // 流结束：冲刷残余（正常情况下后端以空行结尾，这里兜底）
    if (buffer.trim()) dispatchSseEvent(buffer, handlers)
  } catch (err) {
    if (isAbortError(err)) return { kind: 'aborted' }
    return { kind: 'http_error', status: 0, message: '流式连接中断' }
  }
  return { kind: 'ok' }
}

/** 解析单个 SSE 事件：识别 data: / event: done / event: error */
function dispatchSseEvent(raw: string, handlers: StreamHandlers): void {
  if (!raw.trim()) return

  let eventName = 'message'
  const dataLines: string[] = []
  for (const line of raw.split('\n')) {
    const clean = line.endsWith('\r') ? line.slice(0, -1) : line
    if (clean.startsWith('event:')) {
      eventName = clean.slice(6).trim()
    } else if (clean.startsWith('data:')) {
      dataLines.push(clean.slice(5).replace(/^ /, ''))
    }
  }
  const data = dataLines.join('\n')

  if (eventName === 'done') {
    handlers.onDone?.()
    return
  }
  if (eventName === 'error') {
    try {
      handlers.onSseError(JSON.parse(data) as SseErrorPayload)
    } catch {
      handlers.onSseError({ errorType: 'unknown', message: data || '未知错误', retryable: false })
    }
    return
  }
  if (data) handlers.onChunk(data)
}

function isAbortError(err: unknown): boolean {
  return err instanceof DOMException && err.name === 'AbortError'
}

/** 从 SSE 建立前的 HTTP 错误响应中提取后端 message */
async function extractHttpError(resp: Response): Promise<string> {
  try {
    const data = (await resp.json()) as ApiResponse<unknown>
    if (data?.message) return data.message
  } catch {
    // 非 JSON 响应
  }
  return `请求失败 (HTTP ${resp.status})`
}
