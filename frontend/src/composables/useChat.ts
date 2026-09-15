import { ref, watch } from 'vue'
import * as chatApi from '../api/chat'
import { handleUnauthorized } from '../api/http'
import type { ChatMessage, Conversation } from '../types'

/**
 * 轻量会话 store（模块级单例，不引入 Pinia）：
 * 状态在所有组件间共享，任何地方调用 useChat() 拿到的是同一份状态。
 */
const ACTIVE_KEY = 'ai_app_active_conv'

const conversations = ref<Conversation[]>([])
const activeId = ref<number | null>(Number(sessionStorage.getItem(ACTIVE_KEY)) || null)

// 记住"上次选中的会话"：刷新页面后自动恢复历史
watch(activeId, (id) => {
  if (id != null) sessionStorage.setItem(ACTIVE_KEY, String(id))
  else sessionStorage.removeItem(ACTIVE_KEY)
})
const messages = ref<ChatMessage[]>([])
const loading = ref(false)
const streaming = ref(false)
const streamText = ref('')
const streamError = ref<string | null>(null)

let abortController: AbortController | null = null

export function useChat() {
  /** 拉取会话列表（新建/删除后刷新）；若记录过上次选中的会话则自动恢复历史 */
  async function loadConversations(): Promise<void> {
    loading.value = true
    try {
      conversations.value = await chatApi.listConversations()
      if (activeId.value != null) {
        if (!conversations.value.some((c) => c.id === activeId.value)) {
          activeId.value = null
          messages.value = []
        } else if (messages.value.length === 0) {
          messages.value = await chatApi.listMessages(activeId.value)
        }
      }
    } finally {
      loading.value = false
    }
  }

  /** 新建会话：直接以首条消息发起 SSE；流结束后刷新列表并选中最新会话 */
  async function createConversation(content: string): Promise<void> {
    const prevActiveId = activeId.value
    const prevMessages = messages.value
    abortCurrent()
    activeId.value = null
    messages.value = []
    streamText.value = ''
    streamError.value = null
    streaming.value = true

    const outcome = await chatApi.streamChat(
      '/api/chat',
      { title: firstLine(content), content, requestId: crypto.randomUUID() },
      newController().signal,
      {
        onChunk: (t) => { streamText.value += t },
        // 不在 done 时清空流式文本：保持显示直到历史加载完成，避免"空白闪烁"
        onSseError: (p) => { streamError.value = p.message },
      },
    )

    abortController = null

    if (outcome.kind === 'http_error') {
      streaming.value = false
      streamText.value = ''
      if (outcome.status === 401) {
        handleUnauthorized()
        return
      }
      // 创建失败（400/409/5xx）：恢复之前的会话视图，只展示错误，不丢失现场
      streamError.value = outcome.message
      activeId.value = prevActiveId
      messages.value = prevMessages
      return
    }

    // 创建成功（或用户中途停止，会话已落库）→ 刷新列表并选中最新会话展示其历史；
    // streaming 保持 true 直到历史就绪，流式块（含完整文本）作为过渡渲染，无缝交接
    await loadConversations()
    if (conversations.value.length > 0) {
      activeId.value = conversations.value[0].id
      messages.value = await chatApi.listMessages(activeId.value)
    }
    streaming.value = false
    streamText.value = ''
  }

  /** 切换会话：中止当前流，加载该会话历史 */
  async function selectConversation(id: number): Promise<void> {
    if (activeId.value === id) return
    abortCurrent()
    activeId.value = id
    streamError.value = null
    loading.value = true
    try {
      messages.value = await chatApi.listMessages(id)
    } finally {
      loading.value = false
    }
  }

  /** 删除会话并刷新列表 */
  async function removeConversation(id: number): Promise<void> {
    if (activeId.value === id) abortCurrent()
    await chatApi.deleteConversation(id)
    if (activeId.value === id) {
      activeId.value = null
      messages.value = []
    }
    await loadConversations()
  }

  /** 进入"新对话"输入态：清空当前视图，等待用户在 Composer 输入 */
  function startNew(): void {
    abortCurrent()
    activeId.value = null
    messages.value = []
    streamError.value = null
  }

  /**
   * 发送消息：无选中会话 → 走新建（createConversation）；否则继续对话。
   * 乐观插入 USER 消息 → POST SSE → 逐 chunk 追加；done 后把流式文本固化为 ASSISTANT。
   */
  async function send(content: string): Promise<void> {
    if (streaming.value) return
    if (activeId.value == null) {
      await createConversation(content)
      return
    }

    // 乐观消息：立即上屏（后端同时落库；刷新后由历史恢复，不会重复）
    messages.value.push({
      id: -Date.now(),
      role: 'user',
      content,
      createdAt: new Date().toISOString(),
    })
    streamText.value = ''
    streamError.value = null
    streaming.value = true

    const outcome = await chatApi.streamChat(
      `/api/chat/${activeId.value}/messages`,
      { content, requestId: crypto.randomUUID() },
      newController().signal,
      {
        onChunk: (t) => { streamText.value += t },
        onDone: () => {
          const finalText = streamText.value
          if (finalText) {
            messages.value.push({
              id: -Date.now(),
              role: 'assistant',
              content: finalText,
              createdAt: new Date().toISOString(),
            })
          }
          streamText.value = ''
        },
        onSseError: (p) => { streamError.value = p.message },
      },
    )

    streaming.value = false
    abortController = null

    if (outcome.kind === 'http_error') {
      // SSE 建立前的失败（400/409/5xx）：后端未保存此轮消息，回滚乐观 USER 消息并展示错误
      if (outcome.status === 401) {
        handleUnauthorized()
        return
      }
      messages.value.pop()
      streamError.value = outcome.message
    }
    // kind === 'aborted'：用户手动停止/离开 —— 保留 USER 消息（已落库），不追加 ASSISTANT
  }

  /** 停止当前流（Composer 的"停止"按钮 / 切换会话 / 页面离开共用） */
  function abortCurrent(): void {
    if (abortController) {
      abortController.abort()
      abortController = null
    }
    streaming.value = false
    streamText.value = ''
    streamError.value = null
  }

  /** 登出/401：清空全部状态 */
  function reset(): void {
    abortCurrent()
    conversations.value = []
    activeId.value = null
    messages.value = []
  }

  return {
    conversations,
    activeId,
    messages,
    loading,
    streaming,
    streamText,
    streamError,
    loadConversations,
    createConversation,
    selectConversation,
    removeConversation,
    startNew,
    send,
    abortCurrent,
    reset,
  }
}

function newController(): AbortController {
  abortController = new AbortController()
  return abortController
}

/** 会话标题：取首行前 30 字（与后端 startConversation 的截断规则一致） */
function firstLine(content: string): string {
  const line = content.split('\n')[0].trim()
  return line.length > 30 ? line.slice(0, 30) : line
}
