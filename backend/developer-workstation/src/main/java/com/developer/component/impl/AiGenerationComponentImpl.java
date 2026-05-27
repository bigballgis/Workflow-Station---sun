package com.developer.component.impl;

import com.developer.component.AiGenerationComponent;
import com.developer.dto.*;
import com.developer.entity.AiSession;
import com.developer.enums.AiDocumentType;
import com.developer.enums.AiMessageRole;
import com.developer.enums.AiMode;
import com.developer.enums.AiPhase;
import com.developer.enums.AiSessionStatus;
import com.developer.exception.AiGenerationException;
import com.developer.exception.AiValidationFailedException;
import com.developer.security.FunctionUnitWorkspaceAccessService;
import com.developer.security.WorkspaceAccessAction;
import com.developer.service.AiGenerationService;
import com.developer.service.AiLockService;
import com.developer.service.AiValidationService;
import com.developer.service.AiWriteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * AI Generation Component Implementation
 * Orchestrates lock management, session management, N8N calls, SSE event streaming, data validation, and write services
 */
@Component
@Slf4j
public class AiGenerationComponentImpl implements AiGenerationComponent {

    private final AiGenerationService aiGenerationService;
    private final AiLockService aiLockService;
    private final AiValidationService aiValidationService;
    private final AiWriteService aiWriteService;
    private final FunctionUnitWorkspaceAccessService functionUnitWorkspaceAccessService;
    private final Executor taskExecutor;
    private final ObjectMapper objectMapper;

    /** Undo snapshot cache: key = functionUnitId → serialized AiGeneratedData JSON */
    private final ConcurrentHashMap<Long, String> undoSnapshots = new ConcurrentHashMap<>();

    /** Undo snapshot TTL cleanup scheduler */
    private final ScheduledExecutorService undoCleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    @PreDestroy
    public void destroy() {
        if (undoCleanupExecutor != null) {
            undoCleanupExecutor.shutdownNow();
        }
    }

    public AiGenerationComponentImpl(AiGenerationService aiGenerationService,
                                     AiLockService aiLockService,
                                     AiValidationService aiValidationService,
                                     AiWriteService aiWriteService,
                                     FunctionUnitWorkspaceAccessService functionUnitWorkspaceAccessService,
                                     Executor taskExecutor,
                                     ObjectMapper objectMapper) {
        this.aiGenerationService = aiGenerationService;
        this.aiLockService = aiLockService;
        this.aiValidationService = aiValidationService;
        this.aiWriteService = aiWriteService;
        this.functionUnitWorkspaceAccessService = functionUnitWorkspaceAccessService;
        this.taskExecutor = taskExecutor;
        this.objectMapper = objectMapper;
    }

    @Override
    public SseEmitter chatStream(AiChatRequest request, String userId) {
        functionUnitWorkspaceAccessService.assertCanAccess(request.getFunctionUnitId(), WorkspaceAccessAction.MODIFY);
        // 1. Renew the lock
        aiLockService.extendLock(request.getFunctionUnitId(), userId);

        // 2. Create or restore a session
        AiSession session;
        boolean isFirstMessage = request.getSessionId() == null || request.getSessionId().isBlank();
        log.info("chatStream: functionUnitId={}, isFirstMessage={}, sessionId={}, phase={}, mode={}",
                request.getFunctionUnitId(), isFirstMessage, request.getSessionId(), request.getPhase(), request.getMode());
        if (isFirstMessage) {
            session = aiGenerationService.createSession(request.getFunctionUnitId(), userId, request.getMode());
        } else {
            session = aiGenerationService.restoreSession(request.getSessionId());
        }

        // 3. Save the user message
        aiGenerationService.saveMessage(session.getSessionId(), AiMessageRole.USER, request.getMessage(), request.getPhase());

        // 4. Load function unit context and prior documents in the main thread (which has Spring transaction context)
        // Cannot be called inside CompletableFuture.runAsync() because @Transactional is thread-bound;
        // ForkJoinPool threads have no transaction context, causing Hibernate lazy loading to fail silently
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

        // 5. Create the chat SSE emitter
        SseEmitter emitter = aiGenerationService.createChatEmitter(request.getFunctionUnitId(), userId);

        // 6. Async N8N call (context and existingDocuments already loaded in main thread)
        final FunctionUnitContextDTO finalContext = context;
        final List<Map<String, String>> finalExistingDocuments = existingDocuments;
        CompletableFuture.runAsync(() -> {
            try {
                log.info("chatStream async: starting N8N call, functionUnitId={}, sessionId={}, contextPresent={}, docsCount={}",
                        request.getFunctionUnitId(), session.getSessionId(),
                        finalContext != null, finalExistingDocuments.size());

                // 6a. Send session_created event so frontend knows the sessionId
                aiGenerationService.sendChatEvent(request.getFunctionUnitId(), userId,
                        AiChatSseEvent.builder().eventType("session")
                                .data(Map.of("sessionId", session.getSessionId().toString())).build());

                // 6b. Call N8N Webhook
                Map<String, Object> n8nResponse = aiGenerationService.callN8NWebhook(
                        session.getSessionId(), request.getMessage(), request.getPhase(), request.getMode(),
                        finalContext, request.getFunctionUnitId(), finalExistingDocuments,
                        request.getRegenerateScope());

                // 6b. Parse N8N response and send SSE events
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
                    // Compute quality score and attach to generated_data event
                    Object generatedDataObj = n8nResponse.get("generatedData");
                    if (generatedDataObj instanceof Map) {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> generatedDataMap = (Map<String, Object>) generatedDataObj;
                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            AiGeneratedData parsedData = mapper.convertValue(generatedDataMap, AiGeneratedData.class);
                            com.developer.dto.AiQualityScore qualityScore = aiValidationService.computeQualityScore(parsedData);
                            generatedDataMap.put("qualityScore", mapper.convertValue(qualityScore, Map.class));
                        } catch (Exception qsEx) {
                            log.warn("Failed to compute quality score, skipping: {}", qsEx.getMessage());
                        }
                    }
                    aiGenerationService.sendChatEvent(request.getFunctionUnitId(), userId,
                            AiChatSseEvent.builder().eventType("generated_data").data(n8nResponse.get("generatedData")).build());
                }

                // 6c. Save AI response message
                if (reply != null) {
                    aiGenerationService.saveMessage(session.getSessionId(), AiMessageRole.ASSISTANT, reply, request.getPhase());
                }

                // 6d. Send done event
                aiGenerationService.sendChatEvent(request.getFunctionUnitId(), userId,
                        AiChatSseEvent.builder().eventType("done").data(null).build());

                // 6e. Complete the emitter
                aiGenerationService.completeChatEmitter(request.getFunctionUnitId(), userId);

            } catch (Exception e) {
                log.error("N8N call failed: functionUnitId={}, sessionId={}", request.getFunctionUnitId(), session.getSessionId(), e);
                // Send structured error event with errorCode and message
                try {
                    String errorCode = (e instanceof com.developer.exception.AiGenerationException aiEx)
                            ? aiEx.getErrorCode()
                            : "AI_UNKNOWN_ERROR";
                    Map<String, Object> errorData = new java.util.LinkedHashMap<>();
                    errorData.put("errorCode", errorCode);
                    errorData.put("message", e.getMessage());

                    // Check if the exception carries degradation info (graceful degradation after N8N retry failure)
                    if (e instanceof com.developer.exception.AiGenerationException aiEx2
                            && aiEx2.getExtraData() != null) {
                        Object degradationOptions = aiEx2.getExtraData().get("degradationOptions");
                        if (degradationOptions != null) {
                            errorData.put("degradationOptions", degradationOptions);
                        }
                        Object lastSuccessTime = aiEx2.getExtraData().get("lastSuccessTime");
                        if (lastSuccessTime != null) {
                            errorData.put("lastSuccessTime", lastSuccessTime);
                        }
                    }

                    aiGenerationService.sendChatEvent(request.getFunctionUnitId(), userId,
                            AiChatSseEvent.builder().eventType("error").data(errorData).build());
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
        functionUnitWorkspaceAccessService.assertCanAccess(functionUnitId, WorkspaceAccessAction.MODIFY);
        // 1. Renew the lock
        aiLockService.extendLock(functionUnitId, userId);

        try {
            // 2. Validate generated data
            AiValidationResult validationResult = aiValidationService.validate(request.getGeneratedData());

            // 3. Throw exception if validation fails
            if (!validationResult.isValid()) {
                throw new AiValidationFailedException(validationResult.getErrors());
            }

            // 4. Save undo snapshot (before clearing old data)
            try {
                String snapshot = buildSnapshotData(functionUnitId);
                undoSnapshots.put(functionUnitId, snapshot);
                undoCleanupExecutor.schedule(() -> undoSnapshots.remove(functionUnitId), 30, TimeUnit.SECONDS);
                log.info("Saved undo snapshot for functionUnitId={}, TTL=30s", functionUnitId);
            } catch (Exception snapshotEx) {
                log.warn("Failed to save undo snapshot for functionUnitId={}, undo will not be available: {}",
                        functionUnitId, snapshotEx.getMessage());
            }

            // 5. Write data
            aiWriteService.applyGeneratedData(functionUnitId, request.getGeneratedData(),
                    request.getRegenerateScope());

            // 6. Update session status
            aiGenerationService.updateSessionStatus(request.getSessionId(), AiSessionStatus.COMPLETED);

            // 7. Send write success event (with warnings)
            Map<String, Object> successData = new LinkedHashMap<>();
            successData.put("functionUnitId", functionUnitId);
            if (validationResult.getWarnings() != null && !validationResult.getWarnings().isEmpty()) {
                successData.put("warnings", validationResult.getWarnings());
            }
            aiGenerationService.sendEventNotification(functionUnitId,
                    AiChatSseEvent.builder().eventType("write_success").data(successData).build());

        } catch (AiValidationFailedException e) {
            throw e;
        } catch (jakarta.persistence.OptimisticLockException e) {
            log.warn("Optimistic lock conflict during apply: functionUnitId={}", functionUnitId, e);
            aiGenerationService.sendEventNotification(functionUnitId,
                    AiChatSseEvent.builder().eventType("write_error").data(Map.of("error", "AI_WRITE_CONFLICT")).build());
            throw new AiGenerationException("AI_WRITE_CONFLICT",
                    "Data was modified by another user, please retry");
        } catch (Exception e) {
            log.error("Failed to apply generated data: functionUnitId={}", functionUnitId, e);
            // Send write error event
            aiGenerationService.sendEventNotification(functionUnitId,
                    AiChatSseEvent.builder().eventType("write_error").data(Map.of("error", e.getMessage())).build());
            throw e;
        }
    }

    /**
     * Undo the last apply operation, restoring data from snapshot cache
     *
     * @param functionUnitId function unit ID
     * @throws AiGenerationException if the undo window has expired (30-second TTL)
     */
    @Override
    public void undoLastApply(Long functionUnitId) {
        functionUnitWorkspaceAccessService.assertCanAccess(functionUnitId, WorkspaceAccessAction.MODIFY);
        String snapshot = undoSnapshots.remove(functionUnitId);
        if (snapshot == null) {
            throw new AiGenerationException("AI_UNDO_EXPIRED", "Undo window has expired (30 seconds)");
        }
        try {
            AiGeneratedData snapshotData = objectMapper.readValue(snapshot, AiGeneratedData.class);
            aiWriteService.applyGeneratedData(functionUnitId, snapshotData, "ALL");
            log.info("Undo applied successfully for functionUnitId={}", functionUnitId);
        } catch (AiGenerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to undo apply for functionUnitId={}", functionUnitId, e);
            throw new AiGenerationException("AI_UNDO_FAILED", "Failed to undo: " + e.getMessage());
        }
    }

    /**
     * Serialize all entity data of the current function unit into a JSON string in AiGeneratedData format
     */
    private String buildSnapshotData(Long functionUnitId) throws Exception {
        // Use serializeFunctionUnitContext to get current data, then convert to AiGeneratedData format
        FunctionUnitContextDTO context = aiGenerationService.serializeFunctionUnitContext(functionUnitId);
        if (context == null) {
            return objectMapper.writeValueAsString(AiGeneratedData.builder().build());
        }
        AiGeneratedData snapshotData = AiGeneratedData.builder()
                .tableDefinitions(context.getTableDefinitions())
                .formDefinitions(context.getFormDefinitions())
                .actionDefinitions(context.getActionDefinitions())
                .decisionDefinitions(context.getDecisionDefinitions())
                .tableRelations(context.getTableRelations())
                .processDefinition(context.getProcessDefinition())
                .icon(context.getIcon())
                .build();
        return objectMapper.writeValueAsString(snapshotData);
    }

    @Override
    public void updateSessionPhase(String sessionId, com.developer.enums.AiPhase phase) {
        aiGenerationService.updateSessionPhase(sessionId, phase);
    }

    /**
     * Get the next phase, return null if already at the last phase
     */
    private AiPhase getNextPhase(AiPhase current) {
        return switch (current) {
            case REQUIREMENTS -> AiPhase.DESIGN;
            case DESIGN -> AiPhase.GENERATION;
            case GENERATION -> null;
        };
    }
}
