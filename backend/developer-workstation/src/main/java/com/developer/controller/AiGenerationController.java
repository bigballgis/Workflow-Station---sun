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
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 *
 * <p>模型调用直连集团 AI gateway（OpenAI 兼容 {@code /chat/completions}），凭证是该用户的 DSP
 * AMToken。2026-07-28～2026-07-29 之间曾因旧的 Activepieces flow 链路（DW POST
 * {@code <ap>/api/v1/webhooks/<flowId>/sync} → piece-ai 的 run_agent → deepseek）废弃而整体停用，
 * 现已由 {@code AiPromptBuilder} / {@code AiGatewayClient} / {@code AiResponseParser} 三件套接回。
 *
 * <p>开关 {@code ai-generation.enabled}（环境变量 {@code AI_GENERATION_ENABLED}）为 false 时整个控制器
 * 不注册，{@code /ai-generation/**} 全部返回 404。开关须**前后端同时打开**：本开关置 true，且前端
 * {@code src/utils/featureFlags.ts} 的 {@code AI_GENERATION_ENABLED} 为 true 并重新构建。
 * 只开一侧：只开后端 = 用户看不到入口；只开前端 = 点进去全 404。
 * 另外还须配 {@code GROUP_AI_GATEWAY_URL}，否则每轮对话以 {@code AI_GATEWAY_NOT_CONFIGURED} 失败。
 */
@RestController
@RequestMapping("/ai-generation")
@ConditionalOnProperty(prefix = "ai-generation", name = "enabled", havingValue = "true")
@Slf4j
@Tag(name = "AI Function Unit Generation", description = "AI-driven function unit generation APIs")
public class AiGenerationController extends BaseController {

    /** 前端从浏览器侧读到 AMToken 后用这个头透传（见 developer-workstation 的 useAiChat）。 */
    private static final String AM_TOKEN_HEADER = "X-AM-Token";

    private final AiGenerationComponent aiGenerationComponent;
    private final I18nService i18nService;

    /**
     * AMToken 在 Cookie 中的名字，与 admin-center 的 {@code sso.dsp.am-token-name} 对齐。
     * 初值不是兜底而是默认：脱离 Spring 直接 new 本控制器（单元测试）时也有可用的名字。
     */
    @Value("${ai-generation.gateway.am-token-name:AMToken}")
    private String amTokenCookieName = "AMToken";

    public AiGenerationController(AiGenerationComponent aiGenerationComponent, I18nService i18nService) {
        this.aiGenerationComponent = aiGenerationComponent;
        this.i18nService = i18nService;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE chat stream")
    @RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")
    public SseEmitter chatStream(@Valid @RequestBody AiChatRequest request,
                                 HttpServletRequest httpRequest) {
        String userId = com.platform.security.util.SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException(i18nService.getMessage("auth.unauthenticated_user")));
        String amToken = resolveAmToken(httpRequest);
        // 只记来源，不记 amTokenPresent=true/false：字段在不等于凭证有效，DW 页面开久了
        // cookie 还在但 DSP 侧已过期，此时 gateway 回 401 而这条日志会显示"有 token"。
        // 真正的判定在 AiGatewayClient（AI_GATEWAY_UNAUTHORIZED），来源用来区分是前端透传还是浏览器 cookie。
        log.info("Chat stream request for functionUnitId={}, userId={}, amTokenSource={}",
                request.getFunctionUnitId(), userId, amTokenSource(httpRequest, amToken));
        return aiGenerationComponent.chatStream(request, userId, amToken);
    }

    /** {@code header} / {@code cookie} / {@code none}——不含任何 token 内容。 */
    private String amTokenSource(HttpServletRequest httpRequest, String resolved) {
        if (resolved == null) {
            return "none";
        }
        String header = httpRequest.getHeader(AM_TOKEN_HEADER);
        return header != null && !header.isBlank() ? "header" : "cookie";
    }

    /**
     * 取该用户的 DSP AMToken，作 AI gateway 的 Bearer 凭证。
     *
     * <p>顺序：前端显式透传的 {@code X-AM-Token} 头 → 浏览器带上来的 AMToken cookie。
     * 两处都没有时返回 null，由 {@code AiGatewayClient} 以 {@code AI_GATEWAY_TOKEN_MISSING} 显式失败——
     * 绝不退化成匿名调用。token 只在内存里流转，不落库、不进日志。</p>
     */
    private String resolveAmToken(HttpServletRequest httpRequest) {
        String header = httpRequest.getHeader(AM_TOKEN_HEADER);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        Cookie[] cookies = httpRequest.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (amTokenCookieName.equalsIgnoreCase(cookie.getName())
                        && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue().trim();
                }
            }
        }
        return null;
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
