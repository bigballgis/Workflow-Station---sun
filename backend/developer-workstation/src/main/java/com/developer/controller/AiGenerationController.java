package com.developer.controller;

import com.developer.component.AiGenerationComponent;
import com.developer.dto.AiChatRequest;
import com.developer.dto.AiMessageResponse;
import com.developer.dto.AiSessionResponse;
import com.developer.dto.ApiResponse;
import com.developer.dto.ApplyGeneratedDataRequest;
import com.developer.dto.ForceUnlockResponseRequest;
import com.developer.dto.LockInfoResponse;
import com.developer.dto.SaveDocumentRequest;
import com.developer.security.RequireDeveloperPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.developer.entity.AiDocument;
import com.developer.enums.AiDocumentType;

import java.util.List;

/**
 * AI 功能单元生成控制器
 */
@RestController
@RequestMapping("/ai-generation")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI 功能单元生成", description = "AI 驱动的功能单元生成相关接口")
public class AiGenerationController {

    private final AiGenerationComponent aiGenerationComponent;

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE 对话流")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public SseEmitter chatStream(@Valid @RequestBody AiChatRequest request,
                                 @RequestHeader(value = "X-User-Id", required = false) String userId) {
        log.info("Chat stream request for functionUnitId={}, userId={}", request.getFunctionUnitId(), userId);
        return aiGenerationComponent.chatStream(request, userId);
    }

    @GetMapping(value = "/events/{functionUnitId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE 事件长连接")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public SseEmitter eventStream(@PathVariable Long functionUnitId,
                                  @RequestHeader(value = "X-User-Id", required = false) String userId) {
        log.info("Event stream registered for functionUnitId={}, userId={}", functionUnitId, userId);
        return aiGenerationComponent.registerEventEmitter(functionUnitId, userId);
    }

    @PostMapping("/lock/{functionUnitId}")
    @Operation(summary = "获取编辑锁")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<LockInfoResponse>> acquireLock(
            @PathVariable Long functionUnitId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        log.info("Acquire lock for functionUnitId={}, userId={}", functionUnitId, userId);
        LockInfoResponse lockInfo = aiGenerationComponent.acquireLock(functionUnitId, userId);
        return ResponseEntity.ok(ApiResponse.success(lockInfo));
    }

    @DeleteMapping("/lock/{functionUnitId}")
    @Operation(summary = "释放编辑锁")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<Void>> releaseLock(
            @PathVariable Long functionUnitId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        log.info("Release lock for functionUnitId={}, userId={}", functionUnitId, userId);
        aiGenerationComponent.releaseLock(functionUnitId, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/lock/{functionUnitId}/force-unlock-request")
    @Operation(summary = "请求强制解锁")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<Void>> requestForceUnlock(
            @PathVariable Long functionUnitId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        log.info("Force unlock request for functionUnitId={}, requesterId={}", functionUnitId, userId);
        aiGenerationComponent.requestForceUnlock(functionUnitId, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/lock/{functionUnitId}/force-unlock-response")
    @Operation(summary = "响应强制解锁请求")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<Void>> respondForceUnlock(
            @PathVariable Long functionUnitId,
            @RequestBody ForceUnlockResponseRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        log.info("Force unlock response for functionUnitId={}, userId={}, accept={}", functionUnitId, userId, request.isAccept());
        aiGenerationComponent.respondForceUnlock(functionUnitId, userId, request.isAccept());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/sessions")
    @Operation(summary = "获取会话列表")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<AiSessionResponse>>> getSessions(
            @RequestParam Long functionUnitId) {
        List<AiSessionResponse> sessions = aiGenerationComponent.getSessions(functionUnitId);
        return ResponseEntity.ok(ApiResponse.success(sessions));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    @Operation(summary = "分页获取消息")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<Page<AiMessageResponse>>> getMessages(
            @PathVariable String sessionId, Pageable pageable) {
        Page<AiMessageResponse> messages = aiGenerationComponent.getMessages(sessionId, pageable);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    @GetMapping("/documents")
    @Operation(summary = "获取文档版本列表")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<AiDocument>>> getDocumentVersions(
            @RequestParam Long functionUnitId,
            @RequestParam(required = false) AiDocumentType documentType) {
        if (documentType == null) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }
        List<AiDocument> documents = aiGenerationComponent.getDocumentVersions(functionUnitId, documentType);
        return ResponseEntity.ok(ApiResponse.success(documents));
    }

    @GetMapping("/documents/version")
    @Operation(summary = "按版本获取文档")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<AiDocument>> getDocumentByVersion(
            @RequestParam Long functionUnitId,
            @RequestParam AiDocumentType documentType,
            @RequestParam Integer version) {
        AiDocument document = aiGenerationComponent.getDocumentByVersion(functionUnitId, documentType, version);
        return ResponseEntity.ok(ApiResponse.success(document));
    }

    @PostMapping("/documents")
    @Operation(summary = "保存用户编辑的文档")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<AiDocument>> saveDocument(
            @Valid @RequestBody SaveDocumentRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        AiDocument saved = aiGenerationComponent.saveDocument(
                request.getFunctionUnitId(), request.getDocumentType(), request.getContent(), userId);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    @PostMapping("/{functionUnitId}/apply")
    @Operation(summary = "应用 AI 生成的数据")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<Void>> applyGeneratedData(
            @PathVariable Long functionUnitId,
            @RequestBody ApplyGeneratedDataRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        log.info("Apply generated data for functionUnitId={}, userId={}", functionUnitId, userId);
        aiGenerationComponent.applyGeneratedData(functionUnitId, request, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
