import { http } from './http'
import type { ApiResponse } from '../types'

/** 后端 FileResponse 的安全视图（不含 storageKey） */
export interface FileItem {
  id: number
  originalFilename: string
  mimeType: string
  size: number
  sha256: string
  createdAt: string
}

/** 文件列表（时间倒序） */
export async function listFiles(): Promise<FileItem[]> {
  const resp = await http.get<ApiResponse<FileItem[]>>('/files')
  return resp.data.data
}

/** 上传：axios 自动设置 multipart/form-data boundary 并携带 JWT */
export async function uploadFile(file: File): Promise<FileItem> {
  const form = new FormData()
  form.append('file', file)
  const resp = await http.post<ApiResponse<FileItem>>('/files', form)
  return resp.data.data
}

/** 下载：走后端 JWT 保护接口（不暴露公开 URL），blob 落地后触发浏览器下载 */
export async function downloadFile(id: number, filename: string): Promise<void> {
  const resp = await http.get(`/files/${id}/download`, { responseType: 'blob' })
  const url = URL.createObjectURL(resp.data as Blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

export async function deleteFile(id: number): Promise<void> {
  await http.delete<ApiResponse<void>>(`/files/${id}`)
}
