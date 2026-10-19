package com.portal.component;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubTableBatchAssignmentProjectionTest {
    @Test
    void generatedTargetIsDisabledInFormResponseWithoutChangingFrozenSnapshot() throws Exception {
        var harness = new BatchTaskFormTransactionHarness("business_key", false);
        harness.enableAssignmentContract(true);
        var before = harness.read();

        var response = harness.component.getTaskFormData("synthetic-task");

        var control = (Map<?, ?>) response.getSubTableBatchCapabilities().get("grid");
        assertThat(((Map<?, ?>) control.get("capabilities")).get("allowBatchAssign")).isEqualTo(false);
        assertThat(harness.read()).isEqualTo(before);
        assertThat(harness.writes).hasValue(0);
    }

    @Test
    void generationBeforeHostPreservesAssignmentAndOtherOperations() throws Exception {
        var harness = new BatchTaskFormTransactionHarness("business_key", false);
        harness.enableAssignmentContract(false);
        var before = harness.read();
        var control = (Map<?, ?>) harness.component.getTaskFormData("synthetic-task").getSubTableBatchCapabilities().get("grid");
        assertThat(((Map<?, ?>) control.get("capabilities")).get("allowBatchAssign")).isEqualTo(true);
        assertThat(((Map<?, ?>) control.get("capabilities")).get("allowBatchDelete")).isEqualTo(true);
        assertThat(harness.read()).isEqualTo(before);
        assertThat(harness.writes).hasValue(0);
    }

    @Test
    void currentTargetIsDisabledWithoutQueryingOtherTaskOrHistoryRows() throws Exception {
        var harness = new BatchTaskFormTransactionHarness("business_key", false);
        harness.enableAssignmentContract(false);
        var controls = harness.component.getTaskFormData("synthetic-task").getSubTableBatchCapabilities();
        clearInvocations(harness.engine);
        var projected = SubTableBatchAssignmentStageGuard.project(controls, "assigned-work", "synthetic-process", harness.engine);
        var control = (Map<?, ?>) projected.get("grid");
        assertThat(((Map<?, ?>) control.get("capabilities")).get("allowBatchAssign")).isEqualTo(false);
        assertThat(((Map<?, ?>) control.get("capabilities")).get("allowBatchDelete")).isEqualTo(true);
        assertThat(((Map<?, ?>) ((Map<?, ?>) controls.get("grid")).get("capabilities")).get("allowBatchAssign")).isEqualTo(true);
        verifyNoInteractions(harness.engine);
    }

    @Test
    void multipleControlsShareOneAuthoritativeStageRead() throws Exception {
        var harness = new BatchTaskFormTransactionHarness("business_key", false);
        harness.enableAssignmentContract(false);
        var controls = harness.component.getTaskFormData("synthetic-task").getSubTableBatchCapabilities();
        var second = new LinkedHashMap<String, Object>();
        ((Map<?, ?>) controls.get("grid")).forEach((key, value) -> second.put((String) key, value));
        second.put("batchControlId", "second");
        clearInvocations(harness.engine);
        var projected = SubTableBatchAssignmentStageGuard.project(Map.of("grid", controls.get("grid"), "second", second),
                "review", "synthetic-process", harness.engine);
        assertThat(projected).hasSize(2);
        verify(harness.engine, times(1)).getProcessInstanceTasks("synthetic-process");
        verify(harness.engine, times(1)).getTaskHistory("synthetic-process");
    }

    @Test
    void unknownHistoryIsAnExplicitReadErrorNotAnEnabledCommand() throws Exception {
        var harness = new BatchTaskFormTransactionHarness("business_key", false);
        harness.enableAssignmentContract(false);
        when(harness.engine.getTaskHistory("synthetic-process")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> harness.component.getTaskFormData("synthetic-task"))
                .hasMessageContaining("history cannot be verified");
        assertThat(harness.writes).hasValue(0);
    }

    @Test
    void ordinaryControlsDoNotIntroduceEngineStageReads() throws Exception {
        var harness = new BatchTaskFormTransactionHarness("business_key", false);
        harness.component.getTaskFormData("synthetic-task");
        verify(harness.engine, never()).getProcessInstanceTasks(anyString());
        verify(harness.engine, never()).getTaskHistory(anyString());
        assertThat(harness.writes).hasValue(0);
    }

    @Test
    void completedHostRemainsReadableWithAssignmentDisabled() throws Exception {
        var harness = new BatchTaskFormTransactionHarness("business_key", false);
        harness.enableAssignmentContract(false);
        var host = new LinkedHashMap<String, Object>();
        host.put("activityId", "review"); host.put("processInstanceId", "synthetic-process");
        host.put("endTime", "completed"); host.put("deleteReason", null);
        when(harness.engine.getTaskHistory("synthetic-process")).thenReturn(Optional.of(java.util.List.of(host)));
        var response = harness.component.getTaskFormData("synthetic-task");
        var control = (Map<?, ?>) response.getSubTableBatchCapabilities().get("grid");
        assertThat(((Map<?, ?>) control.get("capabilities")).get("allowBatchAssign")).isEqualTo(false);
        assertThat(harness.writes).hasValue(0);
    }

    @Test
    void currentAndFutureTargetsKeepIndependentEligibility() throws Exception {
        var harness = new BatchTaskFormTransactionHarness("business_key", false);
        harness.enableAssignmentContract(false);
        var controls = harness.component.getTaskFormData("synthetic-task").getSubTableBatchCapabilities();
        var second = new LinkedHashMap<String, Object>();
        ((Map<?, ?>) controls.get("grid")).forEach((key, value) -> second.put((String) key, value));
        second.put("batchControlId", "future");
        second.put("assignmentConfig", Map.of("taskDefinitionKey", "future-work"));
        var host = new LinkedHashMap<String, Object>();
        host.put("activityId", "assigned-work"); host.put("processInstanceId", "synthetic-process");
        host.put("endTime", null); host.put("deleteReason", null);
        when(harness.engine.getTaskHistory("synthetic-process")).thenReturn(Optional.of(java.util.List.of(host)));
        clearInvocations(harness.engine);
        var projected = SubTableBatchAssignmentStageGuard.project(Map.of("grid", controls.get("grid"), "future", second),
                "assigned-work", "synthetic-process", harness.engine);
        var current = (Map<?, ?>) projected.get("grid");
        var future = (Map<?, ?>) projected.get("future");
        assertThat(((Map<?, ?>) current.get("capabilities")).get("allowBatchAssign")).isEqualTo(false);
        assertThat(((Map<?, ?>) future.get("capabilities")).get("allowBatchAssign")).isEqualTo(true);
        verify(harness.engine, times(1)).getProcessInstanceTasks("synthetic-process");
        verify(harness.engine, times(1)).getTaskHistory("synthetic-process");
    }
}
