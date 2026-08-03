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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * AI Generation Component Implementation
 * Orchestrates lock management, session management, AI gateway calls, SSE event streaming, data validation, and write services
 */
@Component
@Slf4j
public class AiGenerationComponentImpl implements AiGenerationComponent {

    /** Cap for the cause text pushed to the client in a {@code write_error} event. */
    private static final int MAX_WRITE_ERROR_MESSAGE_LENGTH = 300;

    private final AiGenerationService aiGenerationService;
    private final AiLockService aiLockService;
    private final AiValidationService aiValidationService;
    private final AiWriteService aiWriteService;
    private final FunctionUnitWorkspaceAccessService functionUnitWorkspaceAccessService;
    private final Executor taskExecutor;
    private final ObjectMapper objectMapper;

    /** Undo snapshot cache: key = functionUnitId → serialized AiGeneratedData JSON */
    private final ConcurrentHashMap<Long, String> undoSnapshots = new ConcurrentHashMap<>();

    /**
     * Function units with an apply already running. Confirm Apply clears and rewrites the whole
     * component graph, so a second click while the first write is still in flight makes two
     * transactions delete and re-insert the same rows — the losing one then fails on rows the
     * winner already removed. Rejecting the second click keeps that out of the database.
     *
     * <p>Per-instance only: it stops the double-click, which is what users actually hit. Two DW
     * replicas still fall back to the function-unit AI lock plus optimistic locking.</p>
     */
    private final Set<Long> applyInFlight = ConcurrentHashMap.newKeySet();

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
    public SseEmitter chatStream(AiChatRequest request, String userId, String amToken) {
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

        // 6. Async AI webhook call (context and existingDocuments already loaded in main thread)
        final FunctionUnitContextDTO finalContext = context;
        final List<Map<String, String>> finalExistingDocuments = existingDocuments;
        // Guarded sender: if the user stopped this turn and immediately started a new one,
        // this task's emitter is superseded — its stale events (reply/done) must not leak
        // into the new request's stream, and it must not complete the new emitter.
        // Persistence (saveDocument/saveMessage/phase advance) intentionally stays unguarded.
        final java.util.function.Consumer<AiChatSseEvent> emitIfCurrent = ev -> {
            if (aiGenerationService.isChatEmitterSuperseded(request.getFunctionUnitId(), userId, emitter)) {
                log.info("Skipping stale chat SSE event '{}': emitter superseded, functionUnitId={}, userId={}",
                        ev.getEventType(), request.getFunctionUnitId(), userId);
                return;
            }
            aiGenerationService.sendChatEvent(request.getFunctionUnitId(), userId, ev);
        };
        CompletableFuture.runAsync(() -> {
            try {
                log.info("chatStream async: starting AI gateway call, functionUnitId={}, sessionId={}, contextPresent={}, docsCount={}",
                        request.getFunctionUnitId(), session.getSessionId(),
                        finalContext != null, finalExistingDocuments.size());

                // 6a. Send session_created event so frontend knows the sessionId
                emitIfCurrent.accept(AiChatSseEvent.builder().eventType("session")
                                .data(Map.of("sessionId", session.getSessionId().toString())).build());

                // 6b. Call the AI gateway
                Map<String, Object> aiResponse = aiGenerationService.callAiModel(
                        session.getSessionId(), request.getMessage(), request.getPhase(), request.getMode(),
                        finalContext, request.getFunctionUnitId(), finalExistingDocuments,
                        request.getRegenerateScope(), amToken);

                // 6b. Parse the AI response and send SSE events
                // Blank replies are legal (the model may put everything inside the document
                // markers); skip both the token event and persistence so the chat history
                // doesn't accumulate empty assistant bubbles.
                String reply = null;
                if (aiResponse.get("reply") instanceof String replyStr && !replyStr.isBlank()) {
                    reply = replyStr;
                    emitIfCurrent.accept(AiChatSseEvent.builder().eventType("token").data(reply).build());
                }

                if (aiResponse.containsKey("document") && aiResponse.get("document") != null) {
                    String documentContent = (String) aiResponse.get("document");
                    String documentTypeStr = (String) aiResponse.get("documentType");
                    if (documentTypeStr != null) {
                        AiDocumentType documentType = AiDocumentType.valueOf(documentTypeStr);
                        String summary = (String) aiResponse.getOrDefault("documentSummary", "AI generated document");

                        aiGenerationService.saveDocument(request.getFunctionUnitId(), documentType, documentContent, summary, userId);
                        emitIfCurrent.accept(AiChatSseEvent.builder().eventType("document")
                                        .data(Map.of("documentType", documentTypeStr, "content", documentContent)).build());
                    }
                }

                if (Boolean.TRUE.equals(aiResponse.get("phaseComplete")) && request.isRegenerateOnly()) {
                    // 文档卡上的 Regenerate：产物已经落库并通过 document 事件回给前端了，到此为止。
                    // 既不推进相位也不发 phase_complete——否则一次"重出需求文档"会把会话相位倒回
                    // DESIGN，并触发前端自动重跑设计与生成，覆盖用户已经在迭代的产物。
                    log.info("Regenerate-only turn: skipping phase advance, sessionId={}, phase={}",
                            session.getSessionId(), request.getPhase());
                } else if (Boolean.TRUE.equals(aiResponse.get("phaseComplete"))) {
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
                    emitIfCurrent.accept(AiChatSseEvent.builder().eventType("phase_complete").data(request.getPhase().name()).build());
                }

                if (aiResponse.containsKey("generatedData") && aiResponse.get("generatedData") != null) {
                    // Compute quality score and attach to generated_data event
                    Object generatedDataObj = aiResponse.get("generatedData");
                    if (generatedDataObj instanceof Map) {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> generatedDataMap = (Map<String, Object>) generatedDataObj;
                            normalizeTableRelations(extractTableRelations(generatedDataMap));
                            normalizeCrossFieldRules(extractFormDefinitions(generatedDataMap));
                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            AiGeneratedData parsedData = mapper.convertValue(generatedDataMap, AiGeneratedData.class);
                            com.developer.dto.AiQualityScore qualityScore = aiValidationService.computeQualityScore(parsedData);
                            generatedDataMap.put("qualityScore", mapper.convertValue(qualityScore, Map.class));
                        } catch (Exception qsEx) {
                            log.warn("Failed to compute quality score, skipping: {}", qsEx.getMessage());
                        }
                    }
                    emitIfCurrent.accept(AiChatSseEvent.builder().eventType("generated_data").data(aiResponse.get("generatedData")).build());
                }

                // 6c. Save AI response message
                if (reply != null) {
                    aiGenerationService.saveMessage(session.getSessionId(), AiMessageRole.ASSISTANT, reply, request.getPhase());
                }

                // 6d. Send done event
                emitIfCurrent.accept(AiChatSseEvent.builder().eventType("done").data(null).build());

                // 6e. Complete the emitter — only if this task still owns it
                if (!aiGenerationService.isChatEmitterSuperseded(request.getFunctionUnitId(), userId, emitter)) {
                    aiGenerationService.completeChatEmitter(request.getFunctionUnitId(), userId);
                }

            } catch (Exception e) {
                log.error("AI gateway call failed: functionUnitId={}, sessionId={}", request.getFunctionUnitId(), session.getSessionId(), e);
                // Send structured error event with errorCode and message
                try {
                    String errorCode = (e instanceof com.developer.exception.AiGenerationException aiEx)
                            ? aiEx.getErrorCode()
                            : "AI_UNKNOWN_ERROR";
                    Map<String, Object> errorData = new java.util.LinkedHashMap<>();
                    errorData.put("errorCode", errorCode);
                    errorData.put("message", e.getMessage());

                    // Check if the exception carries degradation info (graceful degradation after AI retry failure)
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

                    emitIfCurrent.accept(AiChatSseEvent.builder().eventType("error").data(errorData).build());
                } catch (Exception sendError) {
                    log.error("Failed to send error event", sendError);
                }
                // Complete emitter — only if this task still owns it
                if (!aiGenerationService.isChatEmitterSuperseded(request.getFunctionUnitId(), userId, emitter)) {
                    aiGenerationService.completeChatEmitter(request.getFunctionUnitId(), userId);
                }
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

        if (!applyInFlight.add(functionUnitId)) {
            log.warn("Rejected concurrent apply: functionUnitId={}, userId={}", functionUnitId, userId);
            throw new AiGenerationException("AI_WRITE_IN_PROGRESS",
                    "A previous apply for this function unit is still running, please wait for it to finish");
        }
        try {
            // 2. Normalize model output the platform enum can't express, then validate
            if (request.getGeneratedData() != null) {
                normalizeTableRelations(request.getGeneratedData().getTableRelations());
                normalizeCrossFieldRules(request.getGeneratedData().getFormDefinitions());
            }
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
            sendWriteError(functionUnitId, "AI_WRITE_CONFLICT", null);
            throw new AiGenerationException("AI_WRITE_CONFLICT",
                    "Data was modified by another user, please retry");
        } catch (Exception e) {
            log.error("Failed to apply generated data: functionUnitId={}", functionUnitId, e);
            String errorCode = e instanceof AiGenerationException age ? age.getErrorCode() : "AI_WRITE_FAILED";
            sendWriteError(functionUnitId, errorCode, rootCauseMessage(e));
            throw e;
        } finally {
            applyInFlight.remove(functionUnitId);
        }
    }

    /**
     * Emit the {@code write_error} SSE event.
     *
     * <p>The payload carries {@code errorCode} (translated by the client) and {@code message} (the cause,
     * for codes that have no canned text). It used to be a single {@code error} key that no client ever
     * read, so every failed Apply surfaced as the generic "Data write failed" toast with the real cause
     * only in the backend log. {@code LinkedHashMap} rather than {@code Map.of} because a null message is
     * legal here and {@code Map.of} would throw an NPE from inside the catch block, hiding the original
     * failure entirely.</p>
     */
    private void sendWriteError(Long functionUnitId, String errorCode, String message) {
        Map<String, Object> errorData = new LinkedHashMap<>();
        errorData.put("errorCode", errorCode);
        errorData.put("message", message);
        aiGenerationService.sendEventNotification(functionUnitId,
                AiChatSseEvent.builder().eventType("write_error").data(errorData).build());
    }

    /** Deepest cause message, capped — a JDBC constraint violation is wrapped several layers deep. */
    private String rootCauseMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        message = message.replaceAll("\\s+", " ").trim();
        return message.length() > MAX_WRITE_ERROR_MESSAGE_LENGTH
                ? message.substring(0, MAX_WRITE_ERROR_MESSAGE_LENGTH) + "…"
                : message;
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

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractTableRelations(Map<String, Object> generatedData) {
        Object rels = generatedData.get("tableRelations");
        return rels instanceof List ? (List<Map<String, Object>>) rels : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractFormDefinitions(Map<String, Object> generatedData) {
        Object forms = generatedData.get("formDefinitions");
        return forms instanceof List ? (List<Map<String, Object>>) forms : null;
    }

    /**
     * A crossFieldRule's targetField only decides which of the involved fields the error
     * message attaches to — LLMs routinely omit it because the rule already lists fields[].
     * Default it to the last entry of fields[] (e.g. for [start_date, end_date] the message
     * belongs on end_date) instead of failing validation with FIELD_CONSTRAINT at apply time.
     */
    @SuppressWarnings("unchecked")
    static void normalizeCrossFieldRules(List<Map<String, Object>> formDefinitions) {
        if (formDefinitions == null) {
            return;
        }
        for (Map<String, Object> form : formDefinitions) {
            if (form == null || !(form.get("configJson") instanceof Map)) {
                continue;
            }
            Object rulesObj = ((Map<String, Object>) form.get("configJson")).get("crossFieldRules");
            if (!(rulesObj instanceof List)) {
                continue;
            }
            for (Map<String, Object> rule : (List<Map<String, Object>>) rulesObj) {
                if (rule == null) {
                    continue;
                }
                Object target = rule.get("targetField");
                boolean missing = !(target instanceof String str) || str.isBlank();
                if (missing && rule.get("fields") instanceof List<?> fields && !fields.isEmpty()) {
                    Object last = fields.get(fields.size() - 1);
                    if (last instanceof String lastField && !lastField.isBlank()) {
                        rule.put("targetField", lastField);
                    }
                }
            }
        }
    }

    /**
     * The platform relation enum is ONE_TO_ONE | ONE_TO_MANY | MANY_TO_MANY — there is no
     * MANY_TO_ONE, but LLMs naturally describe child→parent that way. It is semantically the
     * same as ONE_TO_MANY with the two ends swapped, so rewrite it instead of failing
     * validation with INVALID_ENUM at apply time.
     */
    static void normalizeTableRelations(List<Map<String, Object>> tableRelations) {
        if (tableRelations == null) {
            return;
        }
        for (Map<String, Object> relation : tableRelations) {
            if (relation != null && "MANY_TO_ONE".equals(relation.get("relationType"))) {
                Object sourceTable = relation.get("sourceTableName");
                Object sourceField = relation.get("sourceFieldName");
                relation.put("sourceTableName", relation.get("targetTableName"));
                relation.put("sourceFieldName", relation.get("targetFieldName"));
                relation.put("targetTableName", sourceTable);
                relation.put("targetFieldName", sourceField);
                relation.put("relationType", "ONE_TO_MANY");
            }
        }
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
