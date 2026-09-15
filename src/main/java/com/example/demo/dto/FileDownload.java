package com.example.demo.dto;

import org.springframework.core.io.Resource;

/**
 * 文件下载载体 —— Service 不直接操作 HttpServletResponse，
 * 由 Controller 组装 Content-Disposition 等 HTTP 响应头。
 */
public record FileDownload(Resource resource, String filename, String mimeType) {
}
