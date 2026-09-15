package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.CurrentUser;
import com.example.demo.dto.FileDownload;
import com.example.demo.dto.FileParseStatusResponse;
import com.example.demo.dto.FileResponse;
import com.example.demo.dto.ImageAnalysisResponse;
import com.example.demo.dto.ImageAnalysisStatusResponse;
import com.example.demo.model.User;
import com.example.demo.service.ChunkingService;
import com.example.demo.service.FileParseService;
import com.example.demo.service.FileService;
import com.example.demo.service.ImageAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件管理 —— 上传 / 列表 / 查询 / 下载 / 删除。
 * Controller 只做参数路由和 HTTP 响应组装，校验与存储逻辑在 FileService。
 */
@RestController
@RequestMapping("/api/files")
@Tag(name = "文件管理", description = "文件上传、查询、下载与删除")
@SecurityRequirement(name = "BearerAuth")
public class FileController {

    private final FileService fileService;
    private final FileParseService fileParseService;
    private final ImageAnalysisService imageAnalysisService;
    private final ChunkingService chunkingService;

    public FileController(FileService fileService,
                          FileParseService fileParseService,
                          ImageAnalysisService imageAnalysisService,
                          ChunkingService chunkingService) {
        this.fileService = fileService;
        this.fileParseService = fileParseService;
        this.imageAnalysisService = imageAnalysisService;
        this.chunkingService = chunkingService;
    }

    @Operation(summary = "上传文件", description = "multipart/form-data，字段名 file；仅允许 pdf/docx/txt")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "上传成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "非法文件"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "文件过大"),
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileResponse> upload(
            @RequestParam("file") MultipartFile file,
            @Parameter(hidden = true) @CurrentUser User user) {
        return ApiResponse.success(fileService.upload(file, user));
    }

    @Operation(summary = "文件列表", description = "返回当前用户的全部文件元数据（时间倒序）")
    @GetMapping
    public ApiResponse<List<FileResponse>> listFiles(
            @Parameter(hidden = true) @CurrentUser User user) {
        return ApiResponse.success(fileService.listFiles(user));
    }

    @Operation(summary = "查询文件元数据")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "文件不存在"),
    })
    @GetMapping("/{id}")
    public ApiResponse<FileResponse> getFile(
            @Parameter(description = "文件ID") @PathVariable Long id,
            @Parameter(hidden = true) @CurrentUser User user) {
        return ApiResponse.success(fileService.getFile(id, user));
    }

    @Operation(summary = "下载文件", description = "下载内容走受 JWT 保护的接口，不暴露服务器绝对路径")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "文件内容"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "文件不存在"),
    })
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(
            @Parameter(description = "文件ID") @PathVariable Long id,
            @Parameter(hidden = true) @CurrentUser User user) {
        FileDownload download = fileService.downloadFile(id, user);
        String encoded = URLEncoder.encode(download.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(download.mimeType()))
                .body(download.resource());
    }

    @Operation(summary = "删除文件", description = "同时删除数据库记录与物理文件")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权删除"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "文件不存在"),
    })
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteFile(
            @Parameter(description = "文件ID") @PathVariable Long id,
            @Parameter(hidden = true) @CurrentUser User user) {
        fileService.deleteFile(id, user);
        return ApiResponse.success();
    }

    @Operation(summary = "查询文件解析状态", description = "返回 PENDING / PROCESSING / SUCCESS / FAILED")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "文件不存在"),
    })
    @GetMapping("/{id}/parse-status")
    public ApiResponse<FileParseStatusResponse> getParseStatus(
            @Parameter(description = "文件ID") @PathVariable Long id,
            @Parameter(hidden = true) @CurrentUser User user) {
        return ApiResponse.success(fileParseService.getParseStatus(id, user));
    }

    @Operation(summary = "提交/重试解析", description = "首次手动提交或对 FAILED 文件重试")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "已提交"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权操作"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "文件不存在"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "状态冲突（解析中/已成功）"),
    })
    @PostMapping("/{id}/parse")
    public ApiResponse<Void> parse(
            @Parameter(description = "文件ID") @PathVariable Long id,
            @Parameter(hidden = true) @CurrentUser User user) {
        fileParseService.submitParse(id, user);
        return ApiResponse.success();
    }

    @Operation(summary = "提交/重试图片分析", description = "仅支持 jpg/png/webp；首次提交或 FAILED 重试")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "已提交"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "非图片文件"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权操作"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "状态冲突"),
    })
    @PostMapping("/{id}/image-analysis")
    public ApiResponse<Void> analyzeImage(
            @Parameter(description = "文件ID") @PathVariable Long id,
            @Parameter(hidden = true) @CurrentUser User user) {
        imageAnalysisService.submitAnalysis(id, user);
        return ApiResponse.success();
    }

    @Operation(summary = "查询图片分析状态")
    @GetMapping("/{id}/image-analysis/status")
    public ApiResponse<ImageAnalysisStatusResponse> getImageAnalysisStatus(
            @Parameter(description = "文件ID") @PathVariable Long id,
            @Parameter(hidden = true) @CurrentUser User user) {
        return ApiResponse.success(imageAnalysisService.getStatus(id, user));
    }

    @Operation(summary = "查询图片分析结果", description = "仅 SUCCESS 状态有结果")
    @GetMapping("/{id}/image-analysis")
    public ApiResponse<ImageAnalysisResponse> getImageAnalysis(
            @Parameter(description = "文件ID") @PathVariable Long id,
            @Parameter(hidden = true) @CurrentUser User user) {
        return ApiResponse.success(imageAnalysisService.getResult(id, user));
    }

    @Operation(summary = "重新分块", description = "根据最新解析结果重建 RAG Chunk（幂等，删除旧 Chunk 后重建）")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "重建完成"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权操作"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "文件不存在"),
    })
    @PostMapping("/{id}/chunks")
    public ApiResponse<Void> rechunk(
            @Parameter(description = "文件ID") @PathVariable Long id,
            @Parameter(hidden = true) @CurrentUser User user) {
        chunkingService.rechunk(id, user);
        return ApiResponse.success();
    }
}
