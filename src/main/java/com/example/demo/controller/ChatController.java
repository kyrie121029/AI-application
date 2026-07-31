package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.common.CurrentUser;
import com.example.demo.dto.ChatRequest;
import com.example.demo.dto.ConversationResponse;
import com.example.demo.dto.MessageResponse;
import com.example.demo.model.User;
import com.example.demo.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "AI 对话", description = "多轮对话（流式 SSE）")
@SecurityRequirement(name = "BearerAuth")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @Operation(summary = "对话列表")
    @GetMapping
    public ApiResponse<List<ConversationResponse>> listConversations(
            @Parameter(hidden = true) @CurrentUser User user) {
        return ApiResponse.success(chatService.listConversations(user));
    }

    @Operation(summary = "对话历史")
    @GetMapping("/{id}/messages")
    public ApiResponse<List<MessageResponse>> getMessages(
            @PathVariable Long id,
            @Parameter(hidden = true) @CurrentUser User user) {
        return ApiResponse.success(chatService.getMessages(id, user));
    }

    @Operation(summary = "删除对话")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteConversation(
            @PathVariable Long id,
            @Parameter(hidden = true) @CurrentUser User user) {
        chatService.deleteConversation(id, user);
        return ApiResponse.success();
    }

    @Operation(summary = "新建对话（流式 SSE）")
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter startConversation(
            @Valid @RequestBody ChatRequest request,
            @Parameter(hidden = true) @CurrentUser User user) {
        return chatService.startConversation(request, user);
    }

    @Operation(summary = "继续对话（流式 SSE）")
    @PostMapping(value = "/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter continueConversation(
            @PathVariable Long id,
            @Valid @RequestBody ChatRequest request,
            @Parameter(hidden = true) @CurrentUser User user) {
        return chatService.continueConversation(id, request, user);
    }
}
