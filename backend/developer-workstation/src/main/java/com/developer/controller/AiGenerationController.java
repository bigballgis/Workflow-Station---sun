package com.developer.controller;

import com.developer.component.AiGenerationComponent;
import com.developer.dto.AiChatRequest;
import com.developer.dto.AiMessageResponse;
import com.developer.dto.AiSessionResponse;
import com.platform.common.dto.ApiResponse;
import com.developer.dto.ApplyGeneratedDataRequest;
import com.developer.dto.ForceUnlockResponseRequest;
import com.developer.dto.LockInfoResponse;
import com.developer.dto.SaveDocumentRequest;
import com.developer.security.RequireDeveloperPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.platform.security.util.SecurityContextUtils;
import com.developer.entity.AiDocument;
import com.developer.enums.AiDocumentType;
import com.developer.enums.AiPhase;
import com.platform.common.i18n.I18nService;

import java.util.List;

/**
 * AI Function Unit Generation Controller
 */
@RestController
@RequestMapping("/ai-generation")
@Slf4j
@Tag(name = "AI Function Unit Generation", description = "AI-driven function unit generation APIs")
public class AiGenerationController extends BaseController {

    private final AiGenerationComponent aiGenerationComponent;
    private final I18nService i18nService;

    public AiGenerationController(AiGenerationComponent aiGenerationComponent, I18nService i18nService) {
        this.aiGenerationComponent = aiGenerationComponent;
        this.i18nService = i18nService;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE chat stream")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public SseEmitter chatStream(@Valid @RequestBody AiChatRequest request) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        log.info("Chat stream request for functionUnitId={}, userId={}", request.getFunctionUnitId(), userId);
        return aiGenerationComponent.chatStream(request, userId);
    }

    @GetMapping(value = "/events/{functionUnitId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE event stream")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public SseEmitter eventStream(@PathVariable Long functionUnitId) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        log.info("Event stream registered for functionUnitId={}, userId={}", functionUnitId, userId);
        return aiGenerationComponent.registerEventEmitter(functionUnitId, userId);
    }

    @PostMapping("/lock/{functionUnitId}")
    @Operation(summary = "Acquire edit lock")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<LockInfoResponse>> acquireLock(
            @PathVariable Long functionUnitId) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        // Do not use handleRequest: AiLockConflictException etc. must be handled by AiExceptionHandler to map HTTP status codes
        return ResponseEntity.ok(ApiResponse.success(aiGenerationComponent.acquireLock(functionUnitId, userId)));
    }

    @DeleteMapping("/lock/{functionUnitId}")
    @Operation(summary = "Release edit lock")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<Void>> releaseLock(
            @PathVariable Long functionUnitId) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        return handleRequest(() -> {
            aiGenerationComponent.releaseLock(functionUnitId, userId);
            return null;
        });
    }

    @PostMapping("/lock/{functionUnitId}/force-unlock-request")
    @Operation(summary = "Request force unlock")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<Void>> requestForceUnlock(
            @PathVariable Long functionUnitId) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        return handleRequest(() -> {
            aiGenerationComponent.requestForceUnlock(functionUnitId, userId);
            return null;
        });
    }

    @PostMapping("/lock/{functionUnitId}/force-unlock-response")
    @Operation(summary = "Respond to force unlock request")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<Void>> respondForceUnlock(
            @PathVariable Long functionUnitId,
            @RequestBody ForceUnlockResponseRequest request) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        return handleRequest(() -> {
            aiGenerationComponent.respondForceUnlock(functionUnitId, userId, request.isAccept());
            return null;
        });
    }

    @GetMapping("/sessions")
    @Operation(summary = "List sessions")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<AiSessionResponse>>> getSessions(
            @RequestParam Long functionUnitId) {
        return handleRequest(() -> aiGenerationComponent.getSessions(functionUnitId));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    @Operation(summary = "Get messages (paginated)")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<Page<AiMessageResponse>>> getMessages(
            @PathVariable String sessionId, Pageable pageable) {
        return handleRequest(() -> aiGenerationComponent.getMessages(sessionId, pageable));
    }

    @PutMapping("/sessions/{sessionId}/phase")
    @Operation(summary = "Update session phase")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<Void>> updateSessionPhase(
            @PathVariable String sessionId,
            @RequestParam AiPhase phase) {
        return handleRequest(() -> {
            aiGenerationComponent.updateSessionPhase(sessionId, phase);
            return null;
        });
    }

    @GetMapping("/documents")
    @Operation(summary = "List document versions")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<List<AiDocument>>> getDocumentVersions(
            @RequestParam Long functionUnitId,
            @RequestParam(required = false) AiDocumentType documentType) {
        return handleRequest(() -> {
            if (documentType == null) {
                return List.of();
            }
            return aiGenerationComponent.getDocumentVersions(functionUnitId, documentType);
        });
    }

    @GetMapping("/documents/version")
    @Operation(summary = "Get document by version")
    @RequireDeveloperPermission("FUNCTION_UNIT_VIEW")
    public ResponseEntity<ApiResponse<AiDocument>> getDocumentByVersion(
            @RequestParam Long functionUnitId,
            @RequestParam AiDocumentType documentType,
            @RequestParam Integer version) {
        return handleRequest(() -> aiGenerationComponent.getDocumentByVersion(functionUnitId, documentType, version));
    }

    @PostMapping("/documents")
    @Operation(summary = "Save user-edited document")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<AiDocument>> saveDocument(
            @Valid @RequestBody SaveDocumentRequest request) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        return handleRequest(() -> aiGenerationComponent.saveDocument(
                request.getFunctionUnitId(), request.getDocumentType(), request.getContent(), userId));
    }

    @PostMapping("/{functionUnitId}/apply")
    @Operation(summary = "Apply AI generated data")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<Void>> applyGeneratedData(
            @PathVariable Long functionUnitId,
            @Valid @RequestBody ApplyGeneratedDataRequest request) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        aiGenerationComponent.applyGeneratedData(functionUnitId, request, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/{functionUnitId}/undo")
    @Operation(summary = "Undo last AI data apply (30s TTL)")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public ResponseEntity<ApiResponse<Void>> undoLastApply(@PathVariable Long functionUnitId) {
        aiGenerationComponent.undoLastApply(functionUnitId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
