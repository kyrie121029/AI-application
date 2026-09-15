<script setup lang="ts">
import { ref } from 'vue'
import { useChat } from '../composables/useChat'

const chat = useChat()
const text = ref('')

function submit(): void {
  const content = text.value.trim()
  if (!content || chat.streaming.value) return
  text.value = ''
  chat.send(content)
}

// Enter 发送 / Shift+Enter 换行；中文输入法组合期间不触发
function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    submit()
  }
}

function autoGrow(e: Event): void {
  const el = e.target as HTMLTextAreaElement
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}
</script>

<template>
  <div class="composer">
    <div class="composer-box">
      <textarea
        v-model="text"
        class="composer-input"
        rows="2"
        placeholder="输入你的问题…（Enter 发送）"
        @keydown="onKeydown"
        @input="autoGrow"
      ></textarea>
      <button
        v-if="chat.streaming.value"
        class="btn-ghost stop-btn mono"
        type="button"
        @click="chat.abortCurrent()"
      >
        ■ 停止
      </button>
      <button
        v-else
        class="btn-primary send-btn"
        type="button"
        :disabled="!text.trim()"
        @click="submit"
      >
        发送
      </button>
    </div>
    <p class="mono composer-hint">Enter 发送 · Shift+Enter 换行</p>
  </div>
</template>

<style scoped>
.composer {
  padding: 16px 24px 20px;
  background: var(--surface);
}

.composer-box {
  max-width: var(--chat-max);
  margin: 0 auto;
  display: flex;
  align-items: flex-end;
  gap: 10px;
  border: var(--hairline);
  border-radius: var(--radius);
  padding: 10px 10px 10px 14px;
  background: var(--canvas);
  transition: border-color 120ms ease, box-shadow 120ms ease;
}

.composer-box:focus-within {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--focus-ring);
}

.composer-input {
  flex: 1;
  border: none;
  background: transparent;
  resize: none;
  font-size: 15px;
  line-height: 1.6;
  max-height: 160px;
  padding: 4px 0;
}

.composer-input:focus {
  outline: none;
}

.send-btn {
  padding: 8px 18px;
  flex-shrink: 0;
}

.stop-btn {
  padding: 8px 14px;
  flex-shrink: 0;
  color: var(--error);
}

.composer-hint {
  max-width: var(--chat-max);
  margin: 8px auto 0;
  color: var(--faint);
  text-align: right;
}
</style>
