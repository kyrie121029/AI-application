<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import ConversationSidebar from '../components/ConversationSidebar.vue'
import Composer from '../components/Composer.vue'
import MessageItem from '../components/MessageItem.vue'
import { useChat } from '../composables/useChat'

const chat = useChat()
const scrollEl = ref<HTMLElement | null>(null)
const sidebarOpen = ref(false)

const currentTitle = computed(() => {
  const c = chat.conversations.value.find((item) => item.id === chat.activeId.value)
  return c?.title || '新对话'
})

function scrollToBottom(): void {
  if (scrollEl.value) {
    scrollEl.value.scrollTop = scrollEl.value.scrollHeight
  }
}

// 流式 chunk 到达 / 消息数变化 → 自动滚到底部
watch(
  () => [chat.messages.value.length, chat.streamText.value],
  () => {
    if (chat.streaming.value) nextTick(scrollToBottom)
  },
)

// 切换会话后回到顶部
watch(() => chat.activeId.value, () => nextTick(scrollToBottom))

const onBeforeUnload = (): void => {
  chat.abortCurrent() // 刷新/关闭页面：AbortController 取消当前流
}

onMounted(() => {
  window.addEventListener('beforeunload', onBeforeUnload)
  chat.loadConversations()
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', onBeforeUnload)
  chat.abortCurrent() // 路由离开（登出/401）：取消当前流
})
</script>

<template>
  <div class="workbench">
    <ConversationSidebar :open="sidebarOpen" @close="sidebarOpen = false" />

    <main class="chat-main">
      <header class="chat-header">
        <button class="btn-ghost menu-btn" aria-label="打开会话列表" @click="sidebarOpen = true">
          ☰
        </button>
        <h1 class="chat-title">{{ currentTitle }}</h1>
        <span v-if="chat.activeId.value" class="mono conv-id">conv #{{ chat.activeId.value }}</span>
      </header>

      <div ref="scrollEl" class="scroll-area">
        <div class="messages">
          <div v-if="chat.messages.value.length === 0 && !chat.streaming.value" class="empty">
            <p class="mono empty-eyebrow">NEW CONVERSATION</p>
            <p>还没有消息。在下方输入你的第一个问题，开始一段新的对话。</p>
          </div>

          <MessageItem v-for="m in chat.messages.value" :key="m.id" :message="m" />

          <!-- 流式中的助手回复：签名元素（流式光标块） -->
          <div v-if="chat.streaming.value" class="msg msg-assistant">
            <div class="msg-meta mono">assistant · streaming</div>
            <div class="msg-body">
              <span class="msg-text">{{ chat.streamText.value }}</span
              ><span class="stream-caret" aria-hidden="true"></span>
            </div>
          </div>

          <div v-if="chat.streamError.value" class="stream-error" role="alert">
            {{ chat.streamError.value }}
          </div>
        </div>
      </div>

      <Composer />
    </main>
  </div>
</template>

<style scoped>
.workbench {
  display: flex;
  height: 100%;
}

.chat-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--surface);
}

.chat-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 24px;
  border-bottom: var(--hairline);
}

.chat-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-id {
  color: var(--faint);
  margin-left: auto;
  flex-shrink: 0;
}

.menu-btn {
  display: none;
}

.scroll-area {
  flex: 1;
  overflow-y: auto;
}

.messages {
  max-width: var(--chat-max);
  margin: 0 auto;
  padding: 32px 24px 48px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.empty {
  text-align: center;
  color: var(--muted);
  padding: 80px 0;
}

.empty p {
  margin: 0;
}

.empty-eyebrow {
  color: var(--faint);
  margin-bottom: 8px !important;
}

.stream-error {
  border: 1px solid #e6c6bf;
  background: var(--error-soft);
  color: var(--error);
  border-radius: var(--radius);
  padding: 10px 14px;
  font-size: 13px;
}

@media (max-width: 767px) {
  .menu-btn {
    display: inline-flex;
    padding: 6px 10px;
  }

  .chat-header {
    padding: 12px 16px;
  }

  .messages {
    padding: 20px 16px 32px;
  }
}
</style>
