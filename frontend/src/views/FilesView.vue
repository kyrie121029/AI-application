<script setup lang="ts">
import { onMounted, ref } from 'vue'
import ConversationSidebar from '../components/ConversationSidebar.vue'
import * as filesApi from '../api/files'
import { errorMessage } from '../api/http'
import type { FileItem } from '../api/files'

const files = ref<FileItem[]>([])
const loading = ref(false)
const uploading = ref(false)
const error = ref<string | null>(null)
const selected = ref<File | null>(null)
const pendingDeleteId = ref<number | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const sidebarOpen = ref(false)

async function refresh(): Promise<void> {
  loading.value = true
  try {
    files.value = await filesApi.listFiles()
  } catch (err) {
    error.value = errorMessage(err, '加载文件列表失败')
  } finally {
    loading.value = false
  }
}

function onFileChosen(e: Event): void {
  const input = e.target as HTMLInputElement
  selected.value = input.files?.[0] ?? null
  error.value = null
}

async function upload(): Promise<void> {
  if (!selected.value || uploading.value) return
  uploading.value = true
  error.value = null
  try {
    await filesApi.uploadFile(selected.value)
    selected.value = null
    if (fileInput.value) fileInput.value.value = ''
    await refresh() // 上传完成后刷新列表
  } catch (err) {
    error.value = errorMessage(err, '上传失败')
  } finally {
    uploading.value = false
  }
}

async function download(item: FileItem): Promise<void> {
  error.value = null
  try {
    await filesApi.downloadFile(item.id, item.originalFilename)
  } catch (err) {
    error.value = errorMessage(err, '下载失败')
  }
}

async function remove(item: FileItem): Promise<void> {
  pendingDeleteId.value = null
  error.value = null
  try {
    await filesApi.deleteFile(item.id)
    files.value = files.value.filter((f) => f.id !== item.id) // 删除后同步更新列表
  } catch (err) {
    error.value = errorMessage(err, '删除失败')
  }
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

function formatTime(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}

function extensionOf(filename: string): string {
  const dot = filename.lastIndexOf('.')
  return dot < 0 ? '' : filename.slice(dot + 1).toLowerCase()
}

onMounted(refresh)
</script>

<template>
  <div class="workbench">
    <ConversationSidebar :open="sidebarOpen" @close="sidebarOpen = false" />
    <main class="files-main">
      <header class="files-topbar">
        <button class="btn-ghost menu-btn" aria-label="打开导航" @click="sidebarOpen = true">☰</button>
        <span class="mono files-sub">FILE STORAGE · LOCAL</span>
      </header>
      <div class="files-page">
        <header class="files-header">
          <h1 class="files-title">文件</h1>
        </header>

    <!-- 上传区 -->
    <section class="upload-zone">
      <input
        ref="fileInput"
        type="file"
        accept=".pdf,.docx,.txt"
        hidden
        @change="onFileChosen"
      />
      <button class="btn-ghost" type="button" @click="fileInput?.click()">选择文件</button>
      <span v-if="selected" class="mono selected-name">{{ selected.name }} · {{ formatSize(selected.size) }}</span>
      <button
        class="btn-primary"
        type="button"
        :disabled="!selected || uploading"
        @click="upload"
      >
        {{ uploading ? '上传中…' : '上传' }}
      </button>
      <span class="mono upload-hint">pdf / docx / txt · 最大 10MB</span>
    </section>

    <p v-if="error" class="error-line files-error" role="alert">{{ error }}</p>

    <!-- 文件列表 -->
    <section class="file-list">
      <p class="mono list-eyebrow">FILES</p>
      <div v-if="loading" class="mono files-empty">加载中…</div>
      <div v-else-if="files.length === 0" class="files-empty">
        <p class="mono empty-eyebrow">NO FILES</p>
        <p>还没有上传文件。选择一个 pdf / docx / txt 文件开始。</p>
      </div>
      <ul v-else class="file-items">
        <li
          v-for="f in files"
          :key="f.id"
          class="file-item"
          @mouseleave="pendingDeleteId = null"
        >
          <div class="file-main">
            <span class="file-name" :title="f.originalFilename">{{ f.originalFilename }}</span>
            <span class="mono file-meta">
              {{ extensionOf(f.originalFilename).toUpperCase() || 'FILE' }}
              · {{ formatSize(f.size) }}
              · {{ formatTime(f.createdAt) }}
            </span>
          </div>
          <div class="file-actions">
            <button
              v-if="pendingDeleteId === f.id"
              class="btn-ghost file-del-confirm"
              type="button"
              @click="remove(f)"
            >
              确认删除
            </button>
            <template v-else>
              <button class="btn-ghost" type="button" @click="download(f)">下载</button>
              <button
                class="btn-ghost file-del"
                type="button"
                aria-label="删除文件"
                @click="pendingDeleteId = f.id"
              >
                删除
              </button>
            </template>
          </div>
        </li>
      </ul>
    </section>
      </div>
    </main>
  </div>
</template>

<style scoped>
.workbench {
  display: flex;
  height: 100%;
}

.files-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--surface);
  overflow-y: auto;
}

.files-topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 24px;
  border-bottom: var(--hairline);
}

.files-topbar .files-sub {
  margin: 0;
}

.menu-btn {
  display: none;
}

.files-page {
  max-width: 880px;
  margin: 0 auto;
  padding: 40px 24px 64px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  width: 100%;
}

.files-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
}

@media (max-width: 767px) {
  .menu-btn {
    display: inline-flex;
    padding: 6px 10px;
  }
}

.files-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
}

.files-sub {
  color: var(--faint);
  margin: 4px 0 0;
}

.upload-zone {
  display: flex;
  align-items: center;
  gap: 12px;
  border: var(--hairline);
  border-radius: var(--radius);
  background: var(--surface);
  padding: 14px 16px;
  flex-wrap: wrap;
}

.selected-name {
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 320px;
}

.upload-hint {
  color: var(--faint);
  margin-left: auto;
}

.files-error {
  margin: 0;
}

.list-eyebrow {
  color: var(--faint);
  margin: 0 0 8px;
}

.file-items {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.file-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border: var(--hairline);
  border-radius: var(--radius);
  background: var(--surface);
  padding: 12px 16px;
}

.file-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.file-name {
  font-size: 14px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  color: var(--faint);
}

.file-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.file-actions .btn-ghost {
  font-size: 13px;
  padding: 6px 12px;
}

.file-del {
  color: var(--muted);
}

.file-del-confirm {
  color: var(--error);
  border-color: #e6c6bf;
}

.files-empty {
  text-align: center;
  color: var(--muted);
  padding: 48px 0;
  border: var(--hairline);
  border-radius: var(--radius);
  background: var(--surface);
}

.files-empty p {
  margin: 0;
}

.empty-eyebrow {
  color: var(--faint);
  margin-bottom: 8px !important;
}
</style>
