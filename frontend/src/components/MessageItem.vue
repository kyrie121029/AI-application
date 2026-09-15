<script setup lang="ts">
import type { ChatMessage } from '../types'

const props = defineProps<{ message: ChatMessage }>()

function formatTime(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}
</script>

<template>
  <div class="msg" :class="props.message.role === 'user' ? 'msg-user' : 'msg-assistant'">
    <template v-if="props.message.role === 'user'">
      <div class="user-bubble">{{ props.message.content }}</div>
    </template>
    <template v-else>
      <div class="msg-meta mono">assistant · {{ formatTime(props.message.createdAt) }}</div>
      <div class="msg-body"><span class="msg-text">{{ props.message.content }}</span></div>
    </template>
  </div>
</template>

<style scoped>
.msg-user {
  display: flex;
  justify-content: flex-end;
}

.user-bubble {
  background: var(--user-bubble);
  padding: 10px 14px;
  border-radius: 14px 14px 4px 14px;
  max-width: 78%;
  white-space: pre-wrap;
  word-break: break-word;
}

.msg-meta {
  color: var(--faint);
  margin-bottom: 6px;
}

.msg-text {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
