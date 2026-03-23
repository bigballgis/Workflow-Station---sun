package com.developer.component.impl;

import com.developer.component.AiGenerationComponent;
import com.developer.dto.*;
import com.developer.entity.AiSession;
import com.developer.enums.AiDocumentType;
import com.developer.enums.AiMessageRole;
import com.developer.enums.AiMode;
import com.developer.enums.AiPhase;
import com.developer.enums.AiSessionStatus;
import com.developer.exception.AiValidationFailedException;
import com.developer.service.AiGenerationService;
import com.developer.service.AiLockService;
import com.developer.service.AiValidationService;
import com.developer.service.AiWriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * AI 生成功能组件实现
 * 编排锁管理、会话管理、N8N 调用、SSE 事件流、数据校验与写入等服务
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AiGenerationComponentImpl implements AiGenerationComponent {

    private final AiGenerationService aiGenerationService;
    private final AiLockService aiLockService;
    private final AiValidationService aiValidationService;
    private final AiWriteService aiWriteService;
    private final Executor taskExecutor;

    @Override
    public SseEmitter chatStream(AiChatRequest request, String userId) {
        // 1. 续期锁
        aiLockService.extendLock(request.getFunctionUnitId(), userId);

        // 2. 创建或恢复会话
        AiSession session;
        boolean isFirstMessage = request.getSessionId() == null || request.getSessionId().isBlank();
        log.info("chatStream: functionUnitId={}, isFirstMessage={}, sessionId={}, phase={}, mode={}",
                request.getFunctionUnitId(), isFirstMessage, request.getSessionId(), request.getPhase(), request.getMode());
        if (isFirstMessage) {
            session = aiGenerationService.createSession(request.getFunctionUnitId(), userId, request.getMode());
        } else {
            session = aiGenerationService.restoreSession(request.getSessionId());
        }

        // 3. 保存用户消息
        aiGenerationService.saveMessage(session.getSessionId(), AiMessageRole.USER, request.getMessage(), request.getPhase());

        // 4. 在主线程（有 Spring 事务上下文）中加载功能单元上下文和前序文档
        // 不能在 CompletableFuture.runAsync() 中调用，因为 @Transactional 是线程绑定的，
        // ForkJoinPool 线程没有事务上下文，会导致 Hibernate lazy loading 静默失败
        FunctionUnitContextDTO context;
        List<Map<String, String>> existingDocuments;
        try {
            context = aiGenerationService.serializeFunctionUnitContext(request.getFunctionUnitId());
            log.info("chatStream: context loaded, functionUnitId={}, contextName={}",
                    request.getFunctionUnitId(), context != null ? context.getName() : "null");
            existingDocuments = aiGenerationService.getLatestDocuments(
                    request.getFunctionUnitId(), request.getPhase(), request.getMode());
            log.info("chatStream: existingDocuments loaded, count={}, types={}",
                    existingDocuments.size(),
                    existingDocuments.stream().map(d -> d.get("documentType")).collect(java.util.stream.Collectors.joining(",")));
        } catch (Exception e) {
            log.error("Failed to load context/documents: functionUnitId={}", request.getFunctionUnitId(), e);
            // Degrade gracefully: context and documents empty, don't block conversation
            context = null;
            existingDocuments = List.of();
        }

        // 5. 创建对话 SSE emitter
        SseEmitter emitter = aiGenerationService.createChatEmitter(request.getFunctionUnitId(), userId);

        // 6. Async N8N call (context and existingDocuments already loaded in main thread)
        final FunctionUnitContextDTO finalContext = context;
        final List<Map<String, String>> finalExistingDocuments = existingDocuments;
        CompletableFuture.runAsync(() -> {
            try {
                log.info("chatStream async: starting N8N call, functionUnitId={}, sessionId={}, contextPresent={}, docsCount={}",
                        request.getFunctionUnitId(), session.getSessionId(),
                        finalContext != null, finalExistingDocuments.size());

                // 6a. 调用 N8N Webhook
                Map<String, Object> n8nResponse = aiGenerationService.callN8NWebhook(
                        session.getSessionId(), request.getMessage(), request.getPhase(), request.getMode(),
                        finalContext, request.getFunctionUnitId(), finalExistingDocuments);

                // 6b. 解析 N8N 响应并发送 SSE 事件
                String reply = null;
                if (n8nResponse.containsKey("reply")) {
                    reply = (String) n8nResponse.get("reply");
                    aiGenerationService.sendChatEvent(request.getFunctionUnitId(), userId,
                            AiChatSseEvent.builder().eventType("token").data(reply).build());
                }

                if (n8nResponse.containsKey("document") && n8nResponse.get("document") != null) {
                    String documentContent = (String) n8nResponse.get("document");
                    String documentTypeStr = (String) n8nResponse.get("documentType");
                    if (documentTypeStr != null) {
                        AiDocumentType documentType = AiDocumentType.valueOf(documentTypeStr);
                        String summary = (String) n8nResponse.getOrDefault("documentSummary", "AI generated document");

                        aiGenerationService.saveDocument(request.getFunctionUnitId(), documentType, documentContent, summary, userId);
                        aiGenerationService.sendChatEvent(request.getFunctionUnitId(), userId,
                                AiChatSseEvent.builder().eventType("document")
                                        .data(Map.of("documentType", documentTypeStr, "content", documentContent)).build());
                    }
                }

                if (Boolean.TRUE.equals(n8nResponse.get("phaseComplete"))) {
                    // Auto-advance session phase (persisted in backend, not dependent on frontend "next phase" button)
                    AiPhase nextPhase = getNextPhase(request.getPhase());
                    if (nextPhase != null) {
                        try {
                            aiGenerationService.updateSessionPhase(session.getSessionId().toString(), nextPhase);
                            log.info("Auto-advanced session phase: sessionId={}, from={} to={}",
                                    session.getSessionId(), request.getPhase(), nextPhase);
                        } catch (Exception phaseErr) {
                            log.error("Failed to auto-advance phase: sessionId={}", session.getSessionId(), phaseErr);
                        }
                    }
                    aiGenerationService.sendChatEvent(request.getFunctionUnitId(), userId,
                            AiChatSseEvent.builder().eventType("phase_complete").data(request.getPhase().name()).build());
                }

                if (n8nResponse.containsKey("generatedData") && n8nResponse.get("generatedData") != null) {
                    aiGenerationService.sendChatEvent(request.getFunctionUnitId(), userId,
                            AiChatSseEvent.builder().eventType("generated_data").data(n8nResponse.get("generatedData")).build());
                }

                // 6c. 保存 AI 响应消息
                if (reply != null) {
                    aiGenerationService.saveMessage(session.getSessionId(), AiMessageRole.ASSISTANT, reply, request.getPhase());
                }

                // 6d. 发送 done 事件
                aiGenerationService.sendChatEvent(request.getFunctionUnitId(), userId,
                        AiChatSseEvent.builder().eventType("done").data(null).build());

                // 6e. 完成 emitter
                aiGenerationService.completeChatEmitter(request.getFunctionUnitId(), userId);

            } catch (Exception e) {
                log.error("N8N call failed: functionUnitId={}, sessionId={}", request.getFunctionUnitId(), session.getSessionId(), e);
                // Send error event
                try {
                    aiGenerationService.sendChatEvent(request.getFunctionUnitId(), userId,
                            AiChatSseEvent.builder().eventType("error").data(e.getMessage()).build());
                } catch (Exception sendError) {
                    log.error("Failed to send error event", sendError);
                }
                // Complete emitter
                aiGenerationService.completeChatEmitter(request.getFunctionUnitId(), userId);
            }
        }, taskExecutor);

        // 7. Return SseEmitter immediately
        return emitter;
    }

    @Override
    public SseEmitter registerEventEmitter(Long functionUnitId, String userId) {
        return aiGenerationService.createEventEmitter(functionUnitId, userId);
    }

    @Override
    public LockInfoResponse acquireLock(Long functionUnitId, String userId) {
        return aiLockService.tryAcquire(functionUnitId, userId);
    }

    @Override
    public void releaseLock(Long functionUnitId, String userId) {
        aiLockService.release(functionUnitId, userId);
        aiGenerationService.removeEventEmitter(functionUnitId, userId);
    }

    @Override
    public void requestForceUnlock(Long functionUnitId, String requesterId) {
        aiLockService.requestForceUnlock(functionUnitId, requesterId);
        aiGenerationService.sendEventNotification(functionUnitId,
                AiChatSseEvent.builder().eventType("force_unlock_request").data(Map.of("requesterId", requesterId)).build());
    }

    @Override
    public void respondForceUnlock(Long functionUnitId, String userId, boolean accept) {
        aiLockService.respondForceUnlock(functionUnitId, userId, accept);
        aiGenerationService.sendEventNotification(functionUnitId,
                AiChatSseEvent.builder().eventType("force_unlock_response").data(Map.of("userId", userId, "accept", accept)).build());
    }

    @Override
    public List<AiSessionResponse> getSessions(Long functionUnitId) {
        return aiGenerationService.getSessionsByFunctionUnitId(functionUnitId);
    }

    @Override
    public Page<AiMessageResponse> getMessages(String sessionId, Pageable pageable) {
        return aiGenerationService.getMessagesPaged(sessionId, pageable);
    }

    @Override
    public List<com.developer.entity.AiDocument> getDocumentVersions(Long functionUnitId, AiDocumentType documentType) {
        return aiGenerationService.getDocumentVersions(functionUnitId, documentType);
    }

    @Override
    public com.developer.entity.AiDocument getDocumentByVersion(Long functionUnitId, AiDocumentType documentType, Integer version) {
        return aiGenerationService.getDocumentByVersion(functionUnitId, documentType, version);
    }

    @Override
    public com.developer.entity.AiDocument saveDocument(Long functionUnitId, AiDocumentType documentType, String content, String userId) {
        return aiGenerationService.saveDocument(functionUnitId, documentType, content, "User manual edit", userId);
    }

    @Override
    public void applyGeneratedData(Long functionUnitId, ApplyGeneratedDataRequest request, String userId) {
        // 1. 续期锁
        aiLockService.extendLock(functionUnitId, userId);

        try {
            // 2. 校验生成数据
            AiValidationResult validationResult = aiValidationService.validate(request.getGeneratedData());

            // 3. 校验失败则抛出异常
            if (!validationResult.isValid()) {
                throw new AiValidationFailedException(validationResult.getErrors());
            }

            // 4. 写入数据
            aiWriteService.applyGeneratedData(functionUnitId, request.getGeneratedData());

            // 5. 更新会话状态
            aiGenerationService.updateSessionStatus(request.getSessionId(), AiSessionStatus.COMPLETED);

            // 6. 发送写入成功事件
            aiGenerationService.sendEventNotification(functionUnitId,
                    AiChatSseEvent.builder().eventType("write_success").data(Map.of("functionUnitId", functionUnitId)).build());

        } catch (AiValidationFailedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to apply generated data: functionUnitId={}", functionUnitId, e);
            // Send write error event
            aiGenerationService.sendEventNotification(functionUnitId,
                    AiChatSseEvent.builder().eventType("write_error").data(Map.of("error", e.getMessage())).build());
            throw e;
        }
    }

    @Override
    public void updateSessionPhase(String sessionId, com.developer.enums.AiPhase phase) {
        aiGenerationService.updateSessionPhase(sessionId, phase);
    }

    /**
     * 获取下一个阶段，如果已是最后阶段则返回 null
     */
    private AiPhase getNextPhase(AiPhase current) {
        return switch (current) {
            case REQUIREMENTS -> AiPhase.DESIGN;
            case DESIGN -> AiPhase.GENERATION;
            case GENERATION -> null;
        };
    }
}
