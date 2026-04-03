package com.developer.service;

import com.developer.component.impl.AiGenerationComponentImpl;
import com.developer.security.FunctionUnitWorkspaceAccessService;
import com.developer.dto.AiGeneratedData;
import com.developer.dto.ApplyGeneratedDataRequest;
import com.developer.dto.FunctionUnitContextDTO;
import com.developer.exception.AiGenerationException;
import com.developer.service.impl.AiValidationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for AI undo (apply-undo round-trip) and TTL eviction.
 *
 * <p><b>Validates: Requirements 49.1, 49.2, 49.3</b></p>
 */
@Tag("Feature: ai-function-unit-generation-refactor, Property 29: 应用-撤销往返")
class AiUndoProperties {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Property 29: Apply then undo restores original state.
     *
     * <p>After applyGeneratedData, calling undoLastApply should invoke
     * aiWriteService.applyGeneratedData with the snapshot data that was
     * captured before the apply.</p>
     *
     * <p><b>Validates: Requirements 49.1, 49.2</b></p>
     */
    @Property(tries = 100)
    @Label("Property 29: apply then undo restores original state")
    void applyThenUndoRestoresState(@ForAll("arbitraryFunctionUnitId") Long functionUnitId) throws Exception {
        // Setup mocks
        var aiGenerationService = mock(com.developer.service.AiGenerationService.class);
        var aiLockService = mock(com.developer.service.AiLockService.class);
        var aiValidationService = new AiValidationServiceImpl();
        var aiWriteService = mock(com.developer.service.AiWriteService.class);
        var workspaceAccessService = mock(FunctionUnitWorkspaceAccessService.class);
        var taskExecutor = mock(java.util.concurrent.Executor.class);

        AiGenerationComponentImpl component = new AiGenerationComponentImpl(
                aiGenerationService, aiLockService, aiValidationService, aiWriteService,
                workspaceAccessService, taskExecutor, objectMapper);

        // Build a valid context DTO that serializeFunctionUnitContext would return
        FunctionUnitContextDTO contextDTO = FunctionUnitContextDTO.builder()
                .functionUnitId(functionUnitId)
                .tableDefinitions(List.of(Map.of(
                        "tableName", "original_table",
                        "tableType", "MAIN",
                        "fieldDefinitions", List.of(Map.of(
                                "fieldName", "id", "dataType", "BIGINT", "isPrimaryKey", true)))))
                .formDefinitions(List.of())
                .actionDefinitions(List.of())
                .decisionDefinitions(List.of())
                .tableRelations(List.of())
                .build();
        when(aiGenerationService.serializeFunctionUnitContext(functionUnitId)).thenReturn(contextDTO);

        // Build valid generated data for apply
        AiGeneratedData newData = AiGeneratedData.builder()
                .tableDefinitions(List.of(Map.of(
                        "tableName", "new_table",
                        "tableType", "MAIN",
                        "fieldDefinitions", List.of(Map.of(
                                "fieldName", "id", "dataType", "BIGINT", "isPrimaryKey", true)))))
                .build();

        ApplyGeneratedDataRequest request = new ApplyGeneratedDataRequest();
        request.setGeneratedData(newData);
        request.setSessionId("00000000-0000-0000-0000-000000000001");

        // Apply
        component.applyGeneratedData(functionUnitId, request, "user1");

        // Verify write was called with new data
        verify(aiWriteService).applyGeneratedData(eq(functionUnitId), eq(newData), any());

        // Undo
        reset(aiWriteService);
        component.undoLastApply(functionUnitId);

        // Verify write was called with snapshot data containing original_table
        verify(aiWriteService).applyGeneratedData(eq(functionUnitId), argThat(snapshotData ->
                snapshotData.getTableDefinitions() != null
                        && !snapshotData.getTableDefinitions().isEmpty()
                        && "original_table".equals(snapshotData.getTableDefinitions().get(0).get("tableName"))
        ), eq("ALL"));
    }

    /**
     * Property 30: Undo cache TTL eviction — after removal, undoLastApply throws AI_UNDO_EXPIRED.
     *
     * <p>Simulates TTL eviction by directly removing the snapshot from the cache,
     * then verifying that undoLastApply throws the expected exception.</p>
     *
     * <p><b>Validates: Requirements 49.3</b></p>
     */
    @Property(tries = 10)
    @Label("Property 30: undo after TTL eviction throws AI_UNDO_EXPIRED")
    void undoAfterEvictionThrowsExpired(@ForAll("arbitraryFunctionUnitId") Long functionUnitId) throws Exception {
        var aiGenerationService = mock(com.developer.service.AiGenerationService.class);
        var aiLockService = mock(com.developer.service.AiLockService.class);
        var aiValidationService = new AiValidationServiceImpl();
        var aiWriteService = mock(com.developer.service.AiWriteService.class);
        var workspaceAccessService = mock(FunctionUnitWorkspaceAccessService.class);
        var taskExecutor = mock(java.util.concurrent.Executor.class);

        AiGenerationComponentImpl component = new AiGenerationComponentImpl(
                aiGenerationService, aiLockService, aiValidationService, aiWriteService,
                workspaceAccessService, taskExecutor, objectMapper);

        // Build context for snapshot
        FunctionUnitContextDTO contextDTO = FunctionUnitContextDTO.builder()
                .functionUnitId(functionUnitId)
                .tableDefinitions(List.of())
                .formDefinitions(List.of())
                .actionDefinitions(List.of())
                .decisionDefinitions(List.of())
                .tableRelations(List.of())
                .build();
        when(aiGenerationService.serializeFunctionUnitContext(functionUnitId)).thenReturn(contextDTO);

        // Build valid data and apply
        AiGeneratedData newData = AiGeneratedData.builder()
                .tableDefinitions(List.of(Map.of(
                        "tableName", "test_table",
                        "tableType", "MAIN",
                        "fieldDefinitions", List.of(Map.of(
                                "fieldName", "id", "dataType", "BIGINT", "isPrimaryKey", true)))))
                .build();

        ApplyGeneratedDataRequest request = new ApplyGeneratedDataRequest();
        request.setGeneratedData(newData);
        request.setSessionId("00000000-0000-0000-0000-000000000002");

        component.applyGeneratedData(functionUnitId, request, "user1");

        // Simulate TTL eviction by clearing the snapshot cache
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<Long, String> snapshots =
                (ConcurrentHashMap<Long, String>) ReflectionTestUtils.getField(component, "undoSnapshots");
        assertThat(snapshots).isNotNull();
        snapshots.remove(functionUnitId);

        // Undo should throw AI_UNDO_EXPIRED
        assertThatThrownBy(() -> component.undoLastApply(functionUnitId))
                .isInstanceOf(AiGenerationException.class)
                .satisfies(ex -> {
                    AiGenerationException aiEx = (AiGenerationException) ex;
                    assertThat(aiEx.getErrorCode()).isEqualTo("AI_UNDO_EXPIRED");
                });
    }

    @Provide
    Arbitrary<Long> arbitraryFunctionUnitId() {
        return Arbitraries.longs().between(1L, 10000L);
    }
}
