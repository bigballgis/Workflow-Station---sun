package com.portal.component;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;

class SubTableBatchInitialMiOutcomeTest {
    private final List<SubTableBatchInitialMiOutcome.Expected> expected =
            List.of(new SubTableBatchInitialMiOutcome.Expected("configured-node", true, "synthetic_table", List.of(Map.of("synthetic_key", "one")), List.of()));
    private Map<String, Object> task() {
        return new HashMap<>(Map.of("taskId", "synthetic-task", "processInstanceId", "synthetic-instance",
                "processDefinitionId", "synthetic-definition", "taskDefinitionKey", "configured-node"));
    }
    private com.portal.dto.BatchAssignmentVerification verify(Map<String, Object> task) {
        return SubTableBatchInitialMiOutcome.verify(expected, "synthetic-instance", "synthetic-definition",
                () -> Optional.of(Map.of("tasks", List.of(task), "totalCount", 1)));
    }
    @Test void missingActualPersonIsFailureWithoutThrowing() {
        for (String target : new String[] { null, "", " " }) {
            var task = task(); task.put("currentAssignee", target);
            var result = verify(task);
            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.issues().get(0).reasonCode()).isEqualTo("PERSON_TASK_UNASSIGNED");
            assertThat(result.issues().get(0).taskId()).isEqualTo("synthetic-task");
        }
    }
    @Test void aPopulatedPersonDoesNotProveCorrectRowTarget() {
        var task = task(); task.put("currentAssignee", "synthetic-person");
        assertThat(verify(task).status()).isEqualTo("UNCONFIRMED");
    }
    @Test void anotherNodeCannotProveTheExpectedTaskExists() {
        var task = task(); task.put("taskDefinitionKey", "synthetic-other");
        assertThat(verify(task).status()).isEqualTo("UNCONFIRMED");
    }
    @Test void anotherInstanceOrDeploymentCannotBeUsedToReportFailure() {
        for (String field : List.of("processInstanceId", "processDefinitionId", "taskId")) {
            var task = task(); task.remove(field);
            assertThat(verify(task).status()).isEqualTo("UNCONFIRMED");
        }
    }
    @Test void truncatedQueryNeverClaimsAllRowsSucceeded() {
        var result = SubTableBatchInitialMiOutcome.verify(expected, "synthetic-instance", "synthetic-definition",
                () -> Optional.of(Map.of("tasks", List.of(task()), "totalCount", 200)));
        assertThat(result.status()).isEqualTo("UNCONFIRMED");
        assertThat(result.issues().get(0).reasonCode()).isEqualTo("INCOMPLETE_TASK_QUERY");
    }
    @Test void queryFailureAfterEngineSuccessMustNotThrowOrSuggestRetry() {
        var result = SubTableBatchInitialMiOutcome.verify(expected, "synthetic-instance", "synthetic-definition",
                () -> { throw new IllegalStateException("synthetic-private-error"); });
        assertThat(result.status()).isEqualTo("UNCONFIRMED");
        assertThat(result.toString()).doesNotContain("synthetic-private-error");
    }
    @Test void noNewCapabilityDoesNotQueryEngine() {
        assertThat(SubTableBatchInitialMiOutcome.verify(List.of(), "synthetic-instance", "synthetic-definition",
                () -> { throw new AssertionError("Legacy path must not query"); })).isNull();
    }
    @Test void unclaimedRoleOrMixedNodeCannotBeCalledAnUnassignedPersonalTask() {
        var role = List.of(new SubTableBatchInitialMiOutcome.Expected("configured-node", false, "synthetic_table", List.of(Map.of("synthetic_key", "one")), List.of()));
        var result = SubTableBatchInitialMiOutcome.verify(role, "synthetic-instance", "synthetic-definition",
                () -> Optional.of(Map.of("tasks", List.of(task()), "totalCount", 1)));
        assertThat(result.status()).isEqualTo("UNCONFIRMED");
    }
}
