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
                    List.of(new SubTableBatchInitialMiOutcome.PersonalTarget(Map.of("configured_pk", "own"), "synthetic-person")), List.of())));
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
    private SubTableBatchInitialMiOutcome.Plan mixedPlan() {
        var mixed = new SubTableBatchInitialMiOutcome.Expected("configured-node", false, "configured_table",
                List.of(Map.of("configured_pk", "own"), Map.of("configured_pk", "role")), plan.expected().get(0).people(), List.of());
        return new SubTableBatchInitialMiOutcome.Plan(plan.instanceId(), plan.definitionId(), List.of(mixed));
    }
    private SubTableBatchInitialMiOutcome.Plan rolePlan() {
        var role = new SubTableBatchInitialMiOutcome.RoleTarget(Map.of("configured_pk", "own"), "synthetic-bu", "synthetic-role",
                java.util.Set.of("synthetic-first", "synthetic-second"));
        var expected = new SubTableBatchInitialMiOutcome.Expected("configured-node", false, "configured_table",
                List.of(role.rowKey()), List.of(), List.of(role));
        return new SubTableBatchInitialMiOutcome.Plan(plan.instanceId(), plan.definitionId(), List.of(expected));
    }
    private Map<String, Object> roleTask() {
        var task = live("own-task");
        task.put("currentAssignee", null);
        task.put("assignmentType", "CANDIDATE_USERS");
        task.put("candidateUserIds", List.of("synthetic-second", "synthetic-first"));
        task.put("candidateGroupIds", null);
        task.put("miAssigneeMode", "role");
        task.put("miRoleCode", "synthetic-role");
        task.put("miBusinessUnitCode", "synthetic-bu");
        return task;
    }
    @Test void rolePoolRequiresExactActualCandidatesAndConfiguredContext() {
        responses(List.of(roleTask()), List.of(identity("own-task", "own")));
        var result = SubTableBatchCompletionObservation.observe(rolePlan(), engine);
        assertThat(result.status()).isEqualTo("VERIFIED");
        assertThat(result.issues()).isEmpty();
    }
    @Test void missingOrExtraRoleCandidateIsAnOwnFailureWithoutExposingPeople() {
        for (List<String> users : List.of(List.<String>of(), List.of("synthetic-first"), List.of("synthetic-first", "synthetic-second", "synthetic-extra"))) {
            var task = roleTask(); task.put("candidateUserIds", users);
            responses(List.of(task), List.of(identity("own-task", "own")));
            var result = SubTableBatchCompletionObservation.observe(rolePlan(), engine);
            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.issues()).containsExactly(new com.portal.dto.BatchAssignmentVerification.Issue("own-task", "ROLE_TASK_CANDIDATES_MISMATCH"));
            assertThat(result.toString()).doesNotContain("synthetic-first", "synthetic-second", "synthetic-extra");
        }
    }
    @Test void anotherRoleOrBuCannotBeVerifiedEvenWhenMembersAreTheSame() {
        for (String field : List.of("miRoleCode", "miBusinessUnitCode", "miAssigneeMode")) {
            var task = roleTask(); task.put(field, "synthetic-other");
            responses(List.of(task), List.of(identity("own-task", "own")));
            var result = SubTableBatchCompletionObservation.observe(rolePlan(), engine);
            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.issues()).containsExactly(new com.portal.dto.BatchAssignmentVerification.Issue("own-task", "ROLE_TASK_CONTEXT_MISMATCH"));
        }
    }
    @Test void missingOrMalformedCandidateEvidenceRemainsUnknown() {
        for (String field : List.of("candidateUserIds", "candidateGroupIds", "miRoleCode", "miBusinessUnitCode", "miAssigneeMode", "currentAssignee")) {
            var task = roleTask(); task.remove(field);
            responses(List.of(task), List.of(identity("own-task", "own")));
            assertThat(SubTableBatchCompletionObservation.observe(rolePlan(), engine).status()).isEqualTo("UNCONFIRMED");
        }
    }
    @Test void aClaimedRoleTaskCannotBeCalledAProvenCreationFailure() {
        var task = roleTask(); task.put("currentAssignee", "synthetic-first");
        responses(List.of(task), List.of(identity("own-task", "own")));
        assertThat(SubTableBatchCompletionObservation.observe(rolePlan(), engine).status()).isEqualTo("UNCONFIRMED");
    }
    @Test void roleResultCannotExposeForeignRowTasks() {
        responses(List.of(roleTask()), List.of(identity("own-task", "foreign")));
        var result = SubTableBatchCompletionObservation.observe(rolePlan(), engine);
        assertThat(result.status()).isEqualTo("UNCONFIRMED");
        assertThat(result.toString()).doesNotContain("own-task");
    }
    @Test void extraCandidateGroupsAreNotHiddenByCorrectUserCandidates() {
        var task = roleTask(); task.put("candidateGroupIds", List.of("synthetic-extra-group"));
        responses(List.of(task), List.of(identity("own-task", "own")));
        assertThat(SubTableBatchCompletionObservation.observe(rolePlan(), engine).issues()).containsExactly(
                new com.portal.dto.BatchAssignmentVerification.Issue("own-task", "ROLE_TASK_CANDIDATES_MISMATCH"));
    }
    @Test void duplicateOrMalformedCandidatesAreUnknownRatherThanVerified() {
        for (Object users : List.of(List.of("synthetic-first", "synthetic-first"), List.of(123), "not-a-list")) {
            var task = roleTask(); task.put("candidateUserIds", users);
            responses(List.of(task), List.of(identity("own-task", "own")));
            assertThat(SubTableBatchCompletionObservation.observe(rolePlan(), engine).status()).isEqualTo("UNCONFIRMED");
        }
    }
    @Test void completeMixedTargetsCanVerifyWithoutTurningRoleIntoAPerson() {
        var pool = rolePlan().expected().get(0).roles().get(0);
        var role = new SubTableBatchInitialMiOutcome.RoleTarget(Map.of("configured_pk", "role"), pool.businessUnitCode(), pool.roleCode(), pool.members());
        var mixed = new SubTableBatchInitialMiOutcome.Expected("configured-node", false, "configured_table",
                List.of(Map.of("configured_pk", "own"), role.rowKey()), plan.expected().get(0).people(), List.of(role));
        var person = live("own-task"); person.put("currentAssignee", "synthetic-person");
        var roleTask = roleTask(); roleTask.put("taskId", "role-task");
        responses(List.of(roleTask, person), List.of(identity("own-task", "own"), identity("role-task", "role")));
        var mixedPlan = new SubTableBatchInitialMiOutcome.Plan(plan.instanceId(), plan.definitionId(), List.of(mixed));
        assertThat(SubTableBatchCompletionObservation.observe(mixedPlan, engine).status()).isEqualTo("VERIFIED");
        roleTask.put("candidateUserIds", null);
        assertThat(SubTableBatchCompletionObservation.observe(mixedPlan, engine).issues()).containsExactly(
                new com.portal.dto.BatchAssignmentVerification.Issue("role-task", "ROLE_TASK_CANDIDATES_MISMATCH"));
    }
    @Test void aSingleMemberPoolRemainsAProvenUnclaimedRolePool() {
        var role = new SubTableBatchInitialMiOutcome.RoleTarget(Map.of("configured_pk", "own"), "synthetic-bu", "synthetic-role", java.util.Set.of("synthetic-first"));
        var expected = new SubTableBatchInitialMiOutcome.Expected("configured-node", false, "configured_table", List.of(role.rowKey()), List.of(), List.of(role));
        var task = roleTask(); task.put("candidateUserIds", List.of("synthetic-first"));
        responses(List.of(task), List.of(identity("own-task", "own")));
        assertThat(SubTableBatchCompletionObservation.observe(new SubTableBatchInitialMiOutcome.Plan(
                plan.instanceId(), plan.definitionId(), List.of(expected)), engine).status()).isEqualTo("VERIFIED");
        assertThat(task.get("currentAssignee")).isNull();
    }
    @Test void mixedCorrectPersonAndUnclaimedRoleRemainUnconfirmed() {
        var person = live("own-task"); person.put("currentAssignee", "synthetic-person");
        responses(List.of(person, live("role-task")), List.of(identity("own-task", "own"), identity("role-task", "role")));
        var result = SubTableBatchCompletionObservation.observe(mixedPlan(), engine);
        assertThat(result.status()).isEqualTo("UNCONFIRMED");
        assertThat(result.toString()).doesNotContain("role-task");
    }
    @Test void mixedRoleUncertaintyCannotHideKnownWrongOrMissingPerson() {
        for (String target : List.of("synthetic-wrong-person", "")) {
            var person = live("own-task"); person.put("currentAssignee", target);
            responses(List.of(person, live("role-task")), List.of(identity("own-task", "own"), identity("role-task", "role")));
            var result = SubTableBatchCompletionObservation.observe(mixedPlan(), engine);
            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.issues()).containsExactly(new com.portal.dto.BatchAssignmentVerification.Issue("own-task",
                    target.isBlank() ? "PERSON_TASK_UNASSIGNED" : "PERSON_TASK_TARGET_MISMATCH"));
            assertThat(result.toString()).doesNotContain("role-task", "synthetic-person", "synthetic-wrong-person");
        }
    }
    @Test void mixedForeignPersonalRowCannotLeakFailureOrProveSuccess() {
        responses(List.of(live("foreign-task"), live("role-task")),
                List.of(identity("foreign-task", "foreign"), identity("role-task", "role")));
        var result = SubTableBatchCompletionObservation.observe(mixedPlan(), engine);
        assertThat(result.status()).isEqualTo("UNCONFIRMED");
        assertThat(result.toString()).doesNotContain("foreign-task", "role-task");
    }
    @Test void mixedDuplicatePersonalIdentityCannotFabricateAKnownFailure() {
        responses(List.of(live("own-task"), live("role-task")),
                List.of(identity("own-task", "own"), identity("own-task", "own"), identity("role-task", "role")));
        var result = SubTableBatchCompletionObservation.observe(mixedPlan(), engine);
        assertThat(result.status()).isEqualTo("UNCONFIRMED");
        assertThat(result.toString()).doesNotContain("own-task", "role-task");
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
                        new SubTableBatchInitialMiOutcome.PersonalTarget(Map.of("configured_pk", "second"), "synthetic-second-person")), List.of());
        var task = live("own-task"); task.put("currentAssignee", "synthetic-person");
        responses(List.of(task), List.of(identity("own-task", "own")));
        assertThat(SubTableBatchCompletionObservation.observe(new SubTableBatchInitialMiOutcome.Plan(
                plan.instanceId(), plan.definitionId(), List.of(expected)), engine).status()).isEqualTo("UNCONFIRMED");
    }
    @Test void differentPeopleAreComparedByFullRowKeyRatherThanTaskOrder() {
        var expected = new SubTableBatchInitialMiOutcome.Expected("configured-node", true, "configured_table",
                List.of(Map.of("configured_pk", "own"), Map.of("configured_pk", "second")),
                List.of(new SubTableBatchInitialMiOutcome.PersonalTarget(Map.of("configured_pk", "own"), "synthetic-person"),
                        new SubTableBatchInitialMiOutcome.PersonalTarget(Map.of("configured_pk", "second"), "synthetic-second-person")), List.of());
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
                List.of(Map.of("configured_pk", "own")), List.of(), List.of());
        responses(List.of(live("own-task")), List.of(identity("own-task", "own")));
        assertThat(SubTableBatchCompletionObservation.observe(new SubTableBatchInitialMiOutcome.Plan(
                plan.instanceId(), plan.definitionId(), List.of(role)), engine).status()).isEqualTo("UNCONFIRMED");
    }
    @Test void invalidLiveAssigneeTypeCannotProveAssignment() {
        var task = live("own-task"); task.put("currentAssignee", 123);
        responses(List.of(task), List.of(identity("own-task", "own")));
        assertThat(SubTableBatchCompletionObservation.observe(plan, engine).status()).isEqualTo("UNCONFIRMED");
    }
    @Test void blankPersonalAssigneesRemainExplicitFailures() {
        for (String value : List.of("", " ", "\t")) {
            var task = live("own-task"); task.put("currentAssignee", value);
            responses(List.of(task), List.of(identity("own-task", "own")));
            assertThat(SubTableBatchCompletionObservation.observe(plan, engine).issues())
                    .containsExactly(new com.portal.dto.BatchAssignmentVerification.Issue("own-task", "PERSON_TASK_UNASSIGNED"));
        }
    }
    @Test void failedLiveQueryCannotUndoTheStartOrQueryIdentity() {
        when(engine.getProcessInstanceTasks("synthetic-instance")).thenThrow(new IllegalStateException("synthetic-private-error"));
        var result = SubTableBatchCompletionObservation.observe(plan, engine);
        assertThat(result.status()).isEqualTo("UNCONFIRMED");
        assertThat(result.toString()).doesNotContain("synthetic-private-error");
        verify(engine, never()).getMultiInstanceStatus(anyString());
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
