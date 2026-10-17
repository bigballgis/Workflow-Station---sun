package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubTableBatchCompletionObservationTest {
    private final WorkflowEngineClient engine = mock(WorkflowEngineClient.class);
    private final SubTableBatchInitialMiOutcome.Plan plan = new SubTableBatchInitialMiOutcome.Plan("synthetic-instance", "synthetic-definition",
            List.of(new SubTableBatchInitialMiOutcome.Expected("configured-node", true, "configured_table", List.of(Map.of("configured_pk", "own")),
                    List.of(new SubTableBatchInitialMiOutcome.PersonalTarget(Map.of("configured_pk", "own"), "synthetic-person")))));
    private Map<String, Object> live(String id) {
        return new HashMap<>(Map.of("taskId", id, "processInstanceId", "synthetic-instance",
                "processDefinitionId", "synthetic-definition", "taskDefinitionKey", "configured-node"));
    }
    private Map<String, Object> identity(String id, String key) {
        return new HashMap<>(Map.of("taskId", id, "taskDefinitionKey", "configured-node", "subTableName", "configured_table",
                "subTableRowKey", Map.of("configured_pk", key), "assignee", "synthetic-cached-person"));
    }
    private void responses(List<?> live, List<?> identity) {
        when(engine.getProcessInstanceTasks("synthetic-instance")).thenReturn(Optional.of(Map.of("tasks", live, "totalCount", live.size())));
        when(engine.getMultiInstanceStatus("synthetic-instance")).thenReturn(Optional.of(Map.of("processInstanceId", "synthetic-instance", "tasks", identity)));
    }
    @Test void sameNodeForeignScopeCannotBeReportedAsAnUnassignedPersonalTask() {
        responses(List.of(live("foreign-task")), List.of(identity("foreign-task", "foreign")));
        var result = SubTableBatchCompletionObservation.observe(plan, engine);
        assertThat(result.status()).isEqualTo("UNCONFIRMED");
        assertThat(result.toString()).doesNotContain("foreign-task");
    }
    @Test void onlyOwnFrozenRowCanExposeItsActualMissingAssignee() {
        responses(List.of(live("own-task"), live("foreign-task")), List.of(identity("own-task", "own"), identity("foreign-task", "foreign")));
        var result = SubTableBatchCompletionObservation.observe(plan, engine);
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.issues()).extracting(issue -> issue.taskId()).containsExactly("own-task");
        assertThat(result.toString()).doesNotContain("synthetic-cached-person");
        verify(engine).getProcessInstanceTasks("synthetic-instance");
        verify(engine).getMultiInstanceStatus("synthetic-instance");
        verifyNoMoreInteractions(engine);
    }
    @Test void populatedLiveAssigneeCannotBeOverruledByCachedMiAssignment() {
        var live = live("own-task"); live.put("currentAssignee", "synthetic-person");
        var identity = identity("own-task", "own"); identity.remove("assignee");
        responses(List.of(live), List.of(identity));
        assertThat(SubTableBatchCompletionObservation.observe(plan, engine).status()).isEqualTo("VERIFIED");
    }
    @Test void wrongNonemptyPersonFailsWithoutExposingExpectedOrActualIdentity() {
        var task = live("own-task"); task.put("currentAssignee", "synthetic-wrong-person");
        responses(List.of(task), List.of(identity("own-task", "own")));
        var result = SubTableBatchCompletionObservation.observe(plan, engine);
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.issues()).containsExactly(new com.portal.dto.BatchAssignmentVerification.Issue("own-task", "PERSON_TASK_TARGET_MISMATCH"));
        assertThat(result.toString()).doesNotContain("synthetic-person", "synthetic-wrong-person", "synthetic-cached-person");
    }
    @Test void oneCorrectTaskCannotProveAMissingExpectedRowWasCreated() {
        var expected = new SubTableBatchInitialMiOutcome.Expected("configured-node", true, "configured_table",
                List.of(Map.of("configured_pk", "own"), Map.of("configured_pk", "second")),
                List.of(new SubTableBatchInitialMiOutcome.PersonalTarget(Map.of("configured_pk", "own"), "synthetic-person"),
                        new SubTableBatchInitialMiOutcome.PersonalTarget(Map.of("configured_pk", "second"), "synthetic-second-person")));
        var task = live("own-task"); task.put("currentAssignee", "synthetic-person");
        responses(List.of(task), List.of(identity("own-task", "own")));
        assertThat(SubTableBatchCompletionObservation.observe(new SubTableBatchInitialMiOutcome.Plan(
                plan.instanceId(), plan.definitionId(), List.of(expected)), engine).status()).isEqualTo("UNCONFIRMED");
    }
    @Test void differentPeopleAreComparedByFullRowKeyRatherThanTaskOrder() {
        var expected = new SubTableBatchInitialMiOutcome.Expected("configured-node", true, "configured_table",
                List.of(Map.of("configured_pk", "own"), Map.of("configured_pk", "second")),
                List.of(new SubTableBatchInitialMiOutcome.PersonalTarget(Map.of("configured_pk", "own"), "synthetic-person"),
                        new SubTableBatchInitialMiOutcome.PersonalTarget(Map.of("configured_pk", "second"), "synthetic-second-person")));
        var twoRows = new SubTableBatchInitialMiOutcome.Plan(plan.instanceId(), plan.definitionId(), List.of(expected));
        var first = live("own-task"); first.put("currentAssignee", "synthetic-person");
        var second = live("second-task"); second.put("currentAssignee", "synthetic-second-person");
        responses(List.of(second, first), List.of(identity("own-task", "own"), identity("second-task", "second")));
        assertThat(SubTableBatchCompletionObservation.observe(twoRows, engine).status()).isEqualTo("VERIFIED");
        first.put("currentAssignee", "synthetic-second-person"); second.put("currentAssignee", "synthetic-person");
        var mismatched = SubTableBatchCompletionObservation.observe(twoRows, engine);
        assertThat(mismatched.status()).isEqualTo("FAILED");
        assertThat(mismatched.issues()).hasSize(2).allMatch(issue -> issue.reasonCode().equals("PERSON_TASK_TARGET_MISMATCH"));
    }
    @Test void duplicateActualTasksForOneRowCannotProveSuccess() {
        var first = live("own-task"); first.put("currentAssignee", "synthetic-person");
        var second = live("duplicate-task"); second.put("currentAssignee", "synthetic-person");
        responses(List.of(first, second), List.of(identity("own-task", "own"), identity("duplicate-task", "own")));
        assertThat(SubTableBatchCompletionObservation.observe(plan, engine).status()).isEqualTo("UNCONFIRMED");
    }
    @Test void anAdditionalUnresolvedTaskCannotBeSilentlyExcludedFromSuccess() {
        var task = live("own-task"); task.put("currentAssignee", "synthetic-person");
        for (List<?> metadata : List.of(List.of(identity("own-task", "own")),
                List.of(identity("own-task", "own"), identity("unknown-task", "own"), identity("unknown-task", "own")))) {
            responses(List.of(task, live("unknown-task")), metadata);
            var result = SubTableBatchCompletionObservation.observe(plan, engine);
            assertThat(result.status()).isEqualTo("UNCONFIRMED");
            assertThat(result.toString()).doesNotContain("unknown-task");
        }
    }
    @Test void aConfirmedForeignRowDoesNotInvalidateOwnPersonalSuccess() {
        var task = live("own-task"); task.put("currentAssignee", "synthetic-person");
        responses(List.of(task, live("foreign-task")), List.of(identity("own-task", "own"), identity("foreign-task", "foreign")));
        assertThat(SubTableBatchCompletionObservation.observe(plan, engine).status()).isEqualTo("VERIFIED");
    }
    @Test void missingLiveContextCannotProveAnExtraTaskIsForeign() {
        var own = live("own-task"); own.put("currentAssignee", "synthetic-person");
        for (String field : List.of("processInstanceId", "processDefinitionId", "taskDefinitionKey")) {
            var unknown = live("unknown-task"); unknown.remove(field);
            responses(List.of(own, unknown), List.of(identity("own-task", "own"), identity("unknown-task", "own")));
            var result = SubTableBatchCompletionObservation.observe(plan, engine);
            assertThat(result.status()).isEqualTo("UNCONFIRMED");
            assertThat(result.toString()).doesNotContain("unknown-task");
        }
    }
    @Test void anotherDeploymentCannotProveOwnRowWasAssigned() {
        var task = live("own-task"); task.put("currentAssignee", "synthetic-person"); task.put("processDefinitionId", "another-deployment");
        responses(List.of(task), List.of(identity("own-task", "own")));
        assertThat(SubTableBatchCompletionObservation.observe(plan, engine).status()).isEqualTo("UNCONFIRMED");
    }
    @Test void roleOrUnknownTargetsNeverClaimVerifiedPersonalAssignment() {
        var role = new SubTableBatchInitialMiOutcome.Expected("configured-node", false, "configured_table",
                List.of(Map.of("configured_pk", "own")), List.of());
        responses(List.of(live("own-task")), List.of(identity("own-task", "own")));
        assertThat(SubTableBatchCompletionObservation.observe(new SubTableBatchInitialMiOutcome.Plan(
                plan.instanceId(), plan.definitionId(), List.of(role)), engine).status()).isEqualTo("UNCONFIRMED");
    }
    @Test void invalidLiveAssigneeTypeCannotProveAssignment() {
        var task = live("own-task"); task.put("currentAssignee", 123);
        responses(List.of(task), List.of(identity("own-task", "own")));
        assertThat(SubTableBatchCompletionObservation.observe(plan, engine).status()).isEqualTo("UNCONFIRMED");
    }
    @Test void wrongTableNodeOrKeyShapeCannotAuthorizeObservation() {
        for (String field : List.of("taskDefinitionKey", "subTableName", "subTableRowKey")) {
            var identity = identity("own-task", "own"); identity.remove(field);
            responses(List.of(live("own-task")), List.of(identity));
            assertThat(SubTableBatchCompletionObservation.observe(plan, engine).status()).isEqualTo("UNCONFIRMED");
        }
    }
    @Test void duplicateOrMissingIdentityCannotLeakALiveTaskId() {
        for (List<?> identity : List.of(List.of(), List.of(identity("own-task", "own"), identity("own-task", "own")))) {
            responses(List.of(live("own-task")), identity);
            assertThat(SubTableBatchCompletionObservation.observe(plan, engine).toString()).doesNotContain("own-task");
        }
    }
    @Test void failedIdentityQueryCannotUndoACommittedAction() {
        responses(List.of(live("own-task")), List.of());
        when(engine.getMultiInstanceStatus("synthetic-instance")).thenThrow(new IllegalStateException("synthetic-private-error"));
        var result = SubTableBatchCompletionObservation.observe(plan, engine);
        assertThat(result.status()).isEqualTo("UNCONFIRMED");
        assertThat(result.toString()).doesNotContain("synthetic-private-error", "own-task");
    }
    @Test void incompleteLiveQueryDoesNotTryToProveRowsFromIdentityData() {
        when(engine.getProcessInstanceTasks("synthetic-instance")).thenReturn(Optional.of(Map.of("tasks", List.of(live("own-task")), "totalCount", 200)));
        assertThat(SubTableBatchCompletionObservation.observe(plan, engine).status()).isEqualTo("UNCONFIRMED");
        verify(engine, never()).getMultiInstanceStatus(any());
    }
    @Test void legacyPlanDoesNotReadEitherEndpoint() {
        assertThat(SubTableBatchCompletionObservation.observe(null, engine)).isNull();
        verifyNoInteractions(engine);
    }
}
