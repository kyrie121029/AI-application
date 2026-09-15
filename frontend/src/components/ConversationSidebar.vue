<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { clearToken, getUsername } from '../api/http'
import { useChat } from '../composables/useChat'

defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()

const chat = useChat()
const router = useRouter()
const pendingDeleteId = ref<number | null>(null)

function onNew(): void {
  pendingDeleteId.value = null
  chat.startNew()
  emit('close')
}

function onSelect(id: number): void {
  pendingDeleteId.value = null
  chat.selectConversation(id)
  emit('close')
}

async function onDelete(id: number): Promise<void> {
  pendingDeleteId.value = null
  await chat.removeConversation(id)
}

function onLogout(): void {
  clearToken()
  chat.reset()
  router.push('/login')
}
</script>

<template>
  <aside class="sidebar" :class="{ open }">
    <header class="side-head">
      <div class="brand">
        <span class="brand-mark">AI·APP</span>
        <span class="mono brand-sub">dialogue workbench</span>
      </div>
      <button class="btn-primary new-btn" :disabled="chat.streaming.value" @click="onNew">
        <span class="new-plus">+</span> 新建对话
      </button>
    </header>

    <!-- 模块导航：对话 / 文件 -->
    <nav class="side-nav">
      <RouterLink to="/" class="side-nav-item">对话</RouterLink>
      <RouterLink to="/files" class="side-nav-item">文件</RouterLink>
    </nav>

    <nav class="conv-list">
      <p class="mono list-eyebrow">CONVERSATIONS</p>
      <ul>
        <li
          v-for="c in chat.conversations.value"
          :key="c.id"
          class="conv-item"
          :class="{ active: c.id === chat.activeId.value }"
          @click="onSelect(c.id)"
          @mouseleave="pendingDeleteId = null"
        >
          <span class="conv-title">{{ c.title || '无标题' }}</span>
          <button
            v-if="pendingDeleteId === c.id"
            class="conv-del confirm"
            @click.stop="onDelete(c.id)"
          >
            确认删除
          </button>
          <button v-else class="conv-del" aria-label="删除会话" @click.stop="pendingDeleteId = c.id">
            ×
          </button>
        </li>
        <li v-if="chat.conversations.value.length === 0" class="conv-empty mono">
          {{ chat.loading.value ? '加载中…' : '暂无会话' }}
        </li>
      </ul>
    </nav>

    <footer class="side-foot">
      <span class="mono side-user">{{ getUsername() ?? '用户' }}</span>
      <button class="btn-ghost logout-btn" @click="onLogout">退出登录</button>
    </footer>
  </aside>
</template>

<style scoped>
.sidebar {
  width: var(--sidebar-w);
  min-width: var(--sidebar-w);
  background: var(--canvas);
  border-right: var(--hairline);
  display: flex;
  flex-direction: column;
}

.side-head {
  padding: 16px;
  border-bottom: var(--hairline);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.brand {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.brand-mark {
  font-weight: 700;
  font-size: 16px;
  letter-spacing: 0.02em;
}

.brand-sub {
  color: var(--faint);
}

.new-btn {
  width: 100%;
}

.side-nav {
  display: flex;
  gap: 2px;
  padding: 8px 8px 0;
  border-bottom: var(--hairline);
}

.side-nav-item {
  flex: 1;
  text-align: center;
  padding: 7px 0;
  font-size: 13px;
  color: var(--muted);
  text-decoration: none;
  border-radius: 6px 6px 0 0;
  border-bottom: 2px solid transparent;
}

.side-nav-item:hover {
  color: var(--ink);
}

.side-nav-item.router-link-active {
  color: var(--accent-deep);
  font-weight: 500;
  border-bottom-color: var(--accent);
}

.new-plus {
  font-size: 16px;
  line-height: 1;
}

.conv-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px 8px;
}

.conv-list ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.list-eyebrow {
  color: var(--faint);
  padding: 0 10px 8px;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 10px;
  border-radius: 8px;
  cursor: pointer;
}

.conv-item:hover {
  background: #ecefee;
}

.conv-item.active {
  background: var(--accent-soft);
}

.conv-item.active .conv-title {
  color: var(--accent-deep);
  font-weight: 500;
}

.conv-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}

.conv-del {
  color: var(--faint);
  font-size: 14px;
  line-height: 1;
  padding: 3px 6px;
  border-radius: 4px;
  opacity: 0;
}

.conv-item:hover .conv-del,
.conv-item.active .conv-del {
  opacity: 1;
}

.conv-del.confirm {
  color: var(--error);
  background: var(--error-soft);
  font-family: var(--font-ui);
  font-size: 11px;
  opacity: 1;
}

.conv-empty {
  color: var(--faint);
  padding: 12px 10px;
}

.side-foot {
  padding: 12px 16px;
  border-top: var(--hairline);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.side-user {
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
}

.logout-btn {
  font-size: 12px;
  padding: 6px 10px;
  flex-shrink: 0;
}

@media (max-width: 767px) {
  .sidebar {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    z-index: 20;
    transform: translateX(-100%);
    transition: transform 180ms ease;
    box-shadow: 0 0 24px rgba(0, 0, 0, 0.12);
  }

  .sidebar.open {
    transform: translateX(0);
  }

  .conv-del {
    opacity: 1;
  }
}
</style>
