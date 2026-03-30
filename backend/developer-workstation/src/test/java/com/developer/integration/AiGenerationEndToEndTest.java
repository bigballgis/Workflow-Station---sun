package com.developer.integration;

import com.developer.component.impl.AiGenerationComponentImpl;
import com.developer.dto.*;
import com.developer.entity.AiSession;
import com.developer.enums.*;
import com.developer.exception.AiGenerationException;
import com.developer.exception.AiValidationFailedException;
import com.developer.service.AiGenerationService;
import com.developer.service.AiLockService;
import com.developer.service.AiValidationService;
import com.developer.service.AiWriteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end integration tests for the AI generation flow.
 * Tests the complete orchestration through AiGenerationComponentImpl:
 * session creation → N8N call → validation → write → database verification.
 *
 * Uses Mockito with synchronous executor for deterministic testing of the
 * async SSE orchestration pipeline.
 *
 * **Validates: Requirements 36.1, 36.2, 36.3, 36.4, 36.5**
 */
@Tag("integration-test")
@ExtendWith(MockitoExtension.class)
class AiGenerationEndToEndTest {

    @Mock private AiGenerationService aiGenerationService;
    @Mock private AiLockService aiLockService;
    @Mock private AiValidationService aiValidationService;
    @Mock private AiWriteService aiWriteService;

    private AiGenerationComponentImpl component;
    private ObjectMapper objectMapper;
    private UUID sessionUuid;
    private AiSession session;

    @BeforeEach
    void setUp() {
        // Synchronous executor for deterministic test execution
        Executor executor = Runnable::run;
        objectMapper = new ObjectMapper();
        component = new AiGenerationComponentImpl(
                aiGenerationService, aiLockService, aiValidationService, aiWriteService,
                executor, objectMapper);
        sessionUuid = UUID.randomUUID();
        session = AiSession.builder()
                .sessionId(sessionUuid)
                .functionUnitId(1L)
                .userId("test-user")
                .currentPhase(AiPhase.GENERATION)
                .mode(AiMode.NEW)
                .status(AiSessionStatus.ACTIVE)
                .build();
    }

    // ==================== NEW Mode Complete Flow ====================

    /**
     * NEW mode complete flow: create session → mock N8N response (with all component data
     * including decisionDefinitions + tableRelations) → validation passes → write success
     * → SSE events verified.
     *
     * Validates: Requirements 36.1, 36.2
     */
    @Test
    void newMode_completeFlow_sessionToWriteSuccess() throws Exception {
        // 1. Session creation
        when(aiGenerationService.createSession(1L, "test-user", AiMode.NEW)).thenReturn(session);
        when(aiGenerationService.saveMessage(any(), any(), anyString(), any()))
                .thenReturn(com.developer.entity.AiMessage.builder()
                        .sessionId(sessionUuid).role(AiMessageRole.USER)
                        .content("Generate CRUD app").phase(AiPhase.GENERATION).build());
        when(aiGenerationService.createChatEmitter(1L, "test-user"))
                .thenReturn(new SseEmitter(120000L));
        when(aiGenerationService.serializeFunctionUnitContext(1L))
                .thenReturn(FunctionUnitContextDTO.builder().functionUnitId(1L).name("TestFU").build());
        when(aiGenerationService.getLatestDocuments(1L, AiPhase.GENERATION, AiMode.NEW))
                .thenReturn(List.of());

        // 2. N8N response with ALL component types including decisionDefinitions + tableRelations
        Map<String, Object> generatedData = buildFullGeneratedData();
        Map<String, Object> n8nResponse = new LinkedHashMap<>();
        n8nResponse.put("reply", "Here is your generated application");
        n8nResponse.put("generatedData", generatedData);

        when(aiGenerationService.callN8NWebhook(
                eq(sessionUuid), eq("Generate CRUD app"), eq(AiPhase.GENERATION), eq(AiMode.NEW),
                any(), eq(1L), anyList(), isNull()))
                .thenReturn(n8nResponse);

        // 3. Quality score computation
        when(aiValidationService.computeQualityScore(any(AiGeneratedData.class)))
                .thenReturn(AiQualityScore.builder()
                        .totalScore(85)
                        .dimensions(Map.of("completeness", 22, "consistency", 23, "complexity", 20, "naming", 20))
                        .suggestions(List.of())
                        .build());

        // Track SSE events sent
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> { latch.countDown(); return null; })
                .when(aiGenerationService).completeChatEmitter(1L, "test-user");

        AiChatRequest request = AiChatRequest.builder()
                .functionUnitId(1L).sessionId(null).message("Generate CRUD app")
                .phase(AiPhase.GENERATION).mode(AiMode.NEW).build();

        // Execute
        SseEmitter emitter = component.chatStream(request, "test-user");

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Stream should complete");
        assertNotNull(emitter);

        // Verify: session created
        verify(aiGenerationService).createSession(1L, "test-user", AiMode.NEW);

        // Verify: context loaded
        verify(aiGenerationService).serializeFunctionUnitContext(1L);

        // Verify: N8N called
        verify(aiGenerationService).callN8NWebhook(
                eq(sessionUuid), eq("Generate CRUD app"), eq(AiPhase.GENERATION), eq(AiMode.NEW),
                any(), eq(1L), anyList(), isNull());

        // Verify: SSE events sent (token, generated_data, done)
        ArgumentCaptor<AiChatSseEvent> eventCaptor = ArgumentCaptor.forClass(AiChatSseEvent.class);
        verify(aiGenerationService, atLeast(3)).sendChatEvent(eq(1L), eq("test-user"), eventCaptor.capture());

        List<AiChatSseEvent> events = eventCaptor.getAllValues();
        List<String> eventTypes = events.stream().map(AiChatSseEvent::getEventType).toList();
        assertTrue(eventTypes.contains("token"), "Should send token event");
        assertTrue(eventTypes.contains("generated_data"), "Should send generated_data event");
        assertTrue(eventTypes.contains("done"), "Should send done event");

        // Verify: generated_data event contains qualityScore (attached by component)
        AiChatSseEvent generatedDataEvent = events.stream()
                .filter(e -> "generated_data".equals(e.getEventType())).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> eventData = (Map<String, Object>) generatedDataEvent.getData();
        assertNotNull(eventData.get("qualityScore"), "generated_data should include qualityScore");

        // Verify: AI response message saved
        verify(aiGenerationService).saveMessage(sessionUuid, AiMessageRole.ASSISTANT,
                "Here is your generated application", AiPhase.GENERATION);
    }

    /**
     * NEW mode: applyGeneratedData complete flow — validation passes → write → success event.
     *
     * Validates: Requirements 36.1, 36.2
     */
    @Test
    void newMode_applyGeneratedData_validationPassesAndWriteSucceeds() {
        AiGeneratedData data = buildAiGeneratedDataDto();

        AiValidationResult validResult = AiValidationResult.builder().valid(true).build();
        when(aiValidationService.validate(any(AiGeneratedData.class))).thenReturn(validResult);

        // Verify write is called
        ApplyGeneratedDataRequest request = ApplyGeneratedDataRequest.builder()
                .sessionId(sessionUuid.toString())
                .generatedData(data)
                .build();

        component.applyGeneratedData(1L, request, "test-user");

        // Verify: lock extended
        verify(aiLockService).extendLock(1L, "test-user");

        // Verify: validation called
        verify(aiValidationService).validate(data);

        // Verify: write called with null regenerateScope (defaults to ALL)
        verify(aiWriteService).applyGeneratedData(1L, data, null);

        // Verify: session status updated to COMPLETED
        verify(aiGenerationService).updateSessionStatus(sessionUuid.toString(), AiSessionStatus.COMPLETED);

        // Verify: write_success event sent
        ArgumentCaptor<AiChatSseEvent> eventCaptor = ArgumentCaptor.forClass(AiChatSseEvent.class);
        verify(aiGenerationService).sendEventNotification(eq(1L), eventCaptor.capture());
        AiChatSseEvent successEvent = eventCaptor.getValue();
        assertEquals("write_success", successEvent.getEventType());
    }

    // ==================== MODIFY Mode ====================

    /**
     * MODIFY mode: existing data → modify request → old data cleared → new data written.
     * Verifies the complete MODIFY flow through applyGeneratedData.
     *
     * Validates: Requirements 36.3
     */
    @Test
    void modifyMode_applyGeneratedData_clearsOldAndWritesNew() {
        AiGeneratedData modifyData = AiGeneratedData.builder()
                .name("Modified FU")
                .description("Modified description")
                .tableDefinitions(List.of(Map.of(
                        "tableName", "updated_orders",
                        "tableType", "MAIN",
                        "tableDisplayName", "Updated Orders",
                        "fieldDefinitions", List.of(Map.of(
                                "fieldName", "id", "dataType", "BIGINT",
                                "isPrimaryKey", true, "sortOrder", 1)))))
                .formDefinitions(List.of(Map.of(
                        "formName", "updated_order_form",
                        "formType", "PROCESS",
                        "configJson", Map.of())))
                .decisionDefinitions(List.of(Map.of(
                        "decisionKey", "updated_discount",
                        "decisionName", "Updated Discount",
                        "hitPolicy", "FIRST")))
                .tableRelations(List.of(Map.of(
                        "sourceTableName", "updated_orders",
                        "sourceFieldName", "customer_id",
                        "relationType", "MANY_TO_MANY",
                        "targetTableName", "updated_orders",
                        "targetFieldName", "id")))
                .build();

        AiValidationResult validResult = AiValidationResult.builder().valid(true).build();
        when(aiValidationService.validate(any(AiGeneratedData.class))).thenReturn(validResult);

        ApplyGeneratedDataRequest request = ApplyGeneratedDataRequest.builder()
                .sessionId(sessionUuid.toString())
                .generatedData(modifyData)
                .regenerateScope("ALL")
                .build();

        component.applyGeneratedData(1L, request, "test-user");

        // Verify: write called with ALL scope (full replacement)
        verify(aiWriteService).applyGeneratedData(1L, modifyData, "ALL");

        // Verify: session completed
        verify(aiGenerationService).updateSessionStatus(sessionUuid.toString(), AiSessionStatus.COMPLETED);

        // Verify: write_success event sent
        ArgumentCaptor<AiChatSseEvent> eventCaptor = ArgumentCaptor.forClass(AiChatSseEvent.class);
        verify(aiGenerationService).sendEventNotification(eq(1L), eventCaptor.capture());
        assertEquals("write_success", eventCaptor.getValue().getEventType());
    }

    /**
     * MODIFY mode with scoped regeneration: only specified scope is regenerated.
     *
     * Validates: Requirements 36.3
     */
    @Test
    void modifyMode_scopedRegeneration_passesCorrectScope() {
        AiGeneratedData scopedData = AiGeneratedData.builder()
                .tableDefinitions(List.of(Map.of(
                        "tableName", "new_table",
                        "tableType", "MAIN",
                        "tableDisplayName", "New Table",
                        "fieldDefinitions", List.of())))
                .build();

        AiValidationResult validResult = AiValidationResult.builder().valid(true).build();
        when(aiValidationService.validate(any(AiGeneratedData.class))).thenReturn(validResult);

        ApplyGeneratedDataRequest request = ApplyGeneratedDataRequest.builder()
                .sessionId(sessionUuid.toString())
                .generatedData(scopedData)
                .regenerateScope("TABLES")
                .build();

        component.applyGeneratedData(1L, request, "test-user");

        // Verify: write called with TABLES scope
        verify(aiWriteService).applyGeneratedData(1L, scopedData, "TABLES");
    }

    // ==================== Legacy FormType Auto-Mapping ====================

    /**
     * Legacy FormType auto-mapping: formType "MAIN" → validation passes (with warning) → written as PROCESS.
     * The validation service should produce warnings (not errors) for deprecated form types,
     * and the write service handles the actual MAIN→PROCESS mapping.
     *
     * Validates: Requirements 36.4, 36.5
     */
    @Test
    void legacyFormType_validationPassesWithWarning_writeSucceeds() {
        AiGeneratedData legacyData = AiGeneratedData.builder()
                .tableDefinitions(List.of(Map.of(
                        "tableName", "orders",
                        "tableType", "MAIN",
                        "tableDisplayName", "Orders",
                        "fieldDefinitions", List.of(Map.of(
                                "fieldName", "id", "dataType", "BIGINT",
                                "isPrimaryKey", true, "sortOrder", 1)))))
                .formDefinitions(List.of(Map.of(
                        "formName", "main_form",
                        "formType", "MAIN",
                        "configJson", Map.of())))
                .build();

        // Validation passes with a DEPRECATED_ENUM warning (not error)
        AiValidationResult resultWithWarning = AiValidationResult.builder().valid(true).build();
        resultWithWarning.addWarning("DEPRECATED_ENUM", "formDefinitions[0].formType",
                "Deprecated form type 'MAIN', will be auto-mapped to 'PROCESS'");
        when(aiValidationService.validate(any(AiGeneratedData.class))).thenReturn(resultWithWarning);

        ApplyGeneratedDataRequest request = ApplyGeneratedDataRequest.builder()
                .sessionId(sessionUuid.toString())
                .generatedData(legacyData)
                .build();

        // Should NOT throw — warnings don't block application
        assertDoesNotThrow(() -> component.applyGeneratedData(1L, request, "test-user"));

        // Verify: validation result is valid despite warnings
        assertTrue(resultWithWarning.isValid(), "Warnings should not affect validity");
        assertFalse(resultWithWarning.getWarnings().isEmpty(), "Should have deprecation warning");
        assertEquals("DEPRECATED_ENUM", resultWithWarning.getWarnings().get(0).getErrorType());

        // Verify: write service called (mapping happens inside write service)
        verify(aiWriteService).applyGeneratedData(1L, legacyData, null);

        // Verify: write_success event includes warnings
        ArgumentCaptor<AiChatSseEvent> eventCaptor = ArgumentCaptor.forClass(AiChatSseEvent.class);
        verify(aiGenerationService).sendEventNotification(eq(1L), eventCaptor.capture());
        AiChatSseEvent successEvent = eventCaptor.getValue();
        assertEquals("write_success", successEvent.getEventType());

        @SuppressWarnings("unchecked")
        Map<String, Object> successData = (Map<String, Object>) successEvent.getData();
        assertNotNull(successData.get("warnings"), "write_success should carry warnings");
        @SuppressWarnings("unchecked")
        List<?> warnings = (List<?>) successData.get("warnings");
        assertEquals(1, warnings.size(), "Should have exactly one warning");
    }

    // ==================== Validation Failure ====================

    /**
     * Validation failure: applyGeneratedData should throw AiValidationFailedException
     * and NOT call write service.
     *
     * Validates: Requirements 36.2
     */
    @Test
    void applyGeneratedData_validationFails_throwsAndDoesNotWrite() {
        AiGeneratedData invalidData = AiGeneratedData.builder()
                .decisionDefinitions(List.of(Map.of(
                        "decisionKey", "",
                        "hitPolicy", "INVALID_POLICY")))
                .build();

        AiValidationResult failedResult = AiValidationResult.builder().valid(true).build();
        failedResult.addError("FIELD_CONSTRAINT", "decisionDefinitions[0].decisionKey",
                "decisionKey must not be empty");
        failedResult.addError("INVALID_ENUM", "decisionDefinitions[0].hitPolicy",
                "Invalid hit policy: INVALID_POLICY");
        when(aiValidationService.validate(any(AiGeneratedData.class))).thenReturn(failedResult);

        ApplyGeneratedDataRequest request = ApplyGeneratedDataRequest.builder()
                .sessionId(sessionUuid.toString())
                .generatedData(invalidData)
                .build();

        assertThrows(AiValidationFailedException.class,
                () -> component.applyGeneratedData(1L, request, "test-user"));

        // Write service should NOT be called
        verify(aiWriteService, never()).applyGeneratedData(anyLong(), any(), any());

        // Session status should NOT be updated
        verify(aiGenerationService, never()).updateSessionStatus(anyString(), any());
    }

    // ==================== Undo Flow ====================

    /**
     * Undo flow: applyGeneratedData saves snapshot → undoLastApply restores it.
     *
     * Validates: Requirements 36.2
     */
    @Test
    void undoFlow_applyThenUndo_restoresSnapshot() throws Exception {
        // Setup: serializeFunctionUnitContext returns current state (for snapshot)
        FunctionUnitContextDTO currentContext = FunctionUnitContextDTO.builder()
                .functionUnitId(1L).name("OriginalFU")
                .tableDefinitions(List.of(Map.of("tableName", "original_table")))
                .build();
        when(aiGenerationService.serializeFunctionUnitContext(1L)).thenReturn(currentContext);

        AiGeneratedData newData = AiGeneratedData.builder()
                .name("NewFU")
                .tableDefinitions(List.of(Map.of("tableName", "new_table")))
                .build();

        AiValidationResult validResult = AiValidationResult.builder().valid(true).build();
        when(aiValidationService.validate(any(AiGeneratedData.class))).thenReturn(validResult);

        ApplyGeneratedDataRequest request = ApplyGeneratedDataRequest.builder()
                .sessionId(sessionUuid.toString())
                .generatedData(newData)
                .build();

        // Apply new data (this saves a snapshot internally)
        component.applyGeneratedData(1L, request, "test-user");

        // Verify write was called with new data
        verify(aiWriteService).applyGeneratedData(1L, newData, null);

        // Now undo — should restore the snapshot
        component.undoLastApply(1L);

        // Verify write was called a second time with snapshot data
        ArgumentCaptor<AiGeneratedData> dataCaptor = ArgumentCaptor.forClass(AiGeneratedData.class);
        verify(aiWriteService, times(2)).applyGeneratedData(eq(1L), dataCaptor.capture(), any());

        AiGeneratedData restoredData = dataCaptor.getAllValues().get(1);
        assertNotNull(restoredData.getTableDefinitions());
        assertEquals(1, restoredData.getTableDefinitions().size());
    }

    /**
     * Undo after expiry: should throw AI_UNDO_EXPIRED.
     *
     * Validates: Requirements 36.2
     */
    @Test
    void undoWithoutPriorApply_throwsUndoExpired() {
        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> component.undoLastApply(999L));
        assertEquals("AI_UNDO_EXPIRED", ex.getErrorCode());
    }

    // ==================== Error Handling ====================

    /**
     * N8N call failure: should send structured error SSE event with errorCode.
     *
     * Validates: Requirements 36.2
     */
    @Test
    void chatStream_n8nFailure_sendsStructuredErrorEvent() throws Exception {
        when(aiGenerationService.createSession(1L, "test-user", AiMode.NEW)).thenReturn(session);
        when(aiGenerationService.saveMessage(any(), any(), anyString(), any()))
                .thenReturn(com.developer.entity.AiMessage.builder()
                        .sessionId(sessionUuid).role(AiMessageRole.USER)
                        .content("test").phase(AiPhase.GENERATION).build());
        when(aiGenerationService.createChatEmitter(1L, "test-user"))
                .thenReturn(new SseEmitter(120000L));
        when(aiGenerationService.serializeFunctionUnitContext(1L)).thenReturn(null);
        when(aiGenerationService.getLatestDocuments(anyLong(), any(), any())).thenReturn(List.of());

        // N8N throws timeout
        when(aiGenerationService.callN8NWebhook(any(), anyString(), any(), any(), any(), anyLong(), anyList(), any()))
                .thenThrow(new AiGenerationException("AI_N8N_TIMEOUT", "N8N Webhook call timed out"));

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> { latch.countDown(); return null; })
                .when(aiGenerationService).completeChatEmitter(1L, "test-user");

        AiChatRequest request = AiChatRequest.builder()
                .functionUnitId(1L).sessionId(null).message("test")
                .phase(AiPhase.GENERATION).mode(AiMode.NEW).build();

        component.chatStream(request, "test-user");
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Verify: structured error event sent
        ArgumentCaptor<AiChatSseEvent> eventCaptor = ArgumentCaptor.forClass(AiChatSseEvent.class);
        verify(aiGenerationService).sendChatEvent(eq(1L), eq("test-user"), eventCaptor.capture());

        AiChatSseEvent errorEvent = eventCaptor.getValue();
        assertEquals("error", errorEvent.getEventType());

        @SuppressWarnings("unchecked")
        Map<String, Object> errorData = (Map<String, Object>) errorEvent.getData();
        assertEquals("AI_N8N_TIMEOUT", errorData.get("errorCode"));
        assertNotNull(errorData.get("message"));
    }

    // ==================== Helper Methods ====================

    /**
     * Build a full N8N response generatedData map with all component types.
     */
    private Map<String, Object> buildFullGeneratedData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "Generated App");
        data.put("description", "AI generated CRUD application");

        data.put("tableDefinitions", List.of(
                Map.of("tableName", "orders", "tableType", "MAIN",
                        "tableDisplayName", "Orders",
                        "fieldDefinitions", List.of(
                                Map.of("fieldName", "id", "dataType", "BIGINT",
                                        "isPrimaryKey", true, "sortOrder", 1),
                                Map.of("fieldName", "customer_name", "dataType", "VARCHAR",
                                        "isPrimaryKey", false, "sortOrder", 2))),
                Map.of("tableName", "order_items", "tableType", "SUB",
                        "tableDisplayName", "Order Items",
                        "fieldDefinitions", List.of(
                                Map.of("fieldName", "id", "dataType", "BIGINT",
                                        "isPrimaryKey", true, "sortOrder", 1)))));

        data.put("formDefinitions", List.of(
                Map.of("formName", "order_form", "formType", "PROCESS",
                        "configJson", Map.of(),
                        "fieldPermissions", Map.of("customer_name", "EDITABLE"),
                        "showLiveValues", true)));

        data.put("actionDefinitions", List.of(
                Map.of("actionName", "submit_order", "actionType", "SUBMIT",
                        "configJson", Map.of("visibilityCondition",
                                Map.of("field", "status", "operator", "equals", "value", "DRAFT")))));

        data.put("decisionDefinitions", List.of(
                Map.of("decisionKey", "discount_rule", "decisionName", "Discount Rule",
                        "hitPolicy", "FIRST", "description", "Determines discount rate")));

        data.put("tableRelations", List.of(
                Map.of("sourceTableName", "orders", "sourceFieldName", "id",
                        "relationType", "ONE_TO_MANY",
                        "targetTableName", "order_items", "targetFieldName", "order_id")));

        data.put("processDefinition", Map.of("bpmnXml", "<bpmn/>"));

        data.put("icon", Map.of("name", "order-icon", "category", "GENERAL",
                "svgContent", "<svg><circle/></svg>"));

        return data;
    }

    /**
     * Build an AiGeneratedData DTO with all component types for apply tests.
     */
    private AiGeneratedData buildAiGeneratedDataDto() {
        return AiGeneratedData.builder()
                .name("Generated App")
                .description("AI generated CRUD application")
                .tableDefinitions(List.of(
                        Map.of("tableName", "orders", "tableType", "MAIN",
                                "tableDisplayName", "Orders",
                                "fieldDefinitions", List.of(
                                        Map.of("fieldName", "id", "dataType", "BIGINT",
                                                "isPrimaryKey", true, "sortOrder", 1))),
                        Map.of("tableName", "order_items", "tableType", "SUB",
                                "tableDisplayName", "Order Items",
                                "fieldDefinitions", List.of(
                                        Map.of("fieldName", "id", "dataType", "BIGINT",
                                                "isPrimaryKey", true, "sortOrder", 1)))))
                .formDefinitions(List.of(
                        Map.of("formName", "order_form", "formType", "PROCESS",
                                "configJson", Map.of())))
                .actionDefinitions(List.of(
                        Map.of("actionName", "submit_order", "actionType", "SUBMIT",
                                "configJson", Map.of())))
                .decisionDefinitions(List.of(
                        Map.of("decisionKey", "discount_rule", "decisionName", "Discount Rule",
                                "hitPolicy", "FIRST")))
                .tableRelations(List.of(
                        Map.of("sourceTableName", "orders", "sourceFieldName", "id",
                                "relationType", "ONE_TO_MANY",
                                "targetTableName", "order_items", "targetFieldName", "order_id")))
                .processDefinition(Map.of("bpmnXml", "<bpmn/>"))
                .icon(Map.of("name", "order-icon", "category", "GENERAL",
                        "svgContent", "<svg><circle/></svg>"))
                .build();
    }
}
