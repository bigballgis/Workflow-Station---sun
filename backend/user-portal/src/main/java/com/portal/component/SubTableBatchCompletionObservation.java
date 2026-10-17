package com.portal.component;

import com.platform.common.jdbc.SubTableRowKeySupport;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.BatchAssignmentVerification;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Join existing bulk identity metadata to live Flowable tasks; never trust cached ETI assignees. */
final class SubTableBatchCompletionObservation {
    private SubTableBatchCompletionObservation() { }

    static BatchAssignmentVerification observe(SubTableBatchInitialMiOutcome.Plan plan, WorkflowEngineClient engine) {
        if (plan == null) return null;
        try {
            var live = engine.getProcessInstanceTasks(plan.instanceId()).orElseThrow();
            if (!(live.get("tasks") instanceof List<?> tasks) || !(live.get("totalCount") instanceof Number count)
                    || count.longValue() != tasks.size()) return unconfirmed("INCOMPLETE_TASK_QUERY");
            var identity = engine.getMultiInstanceStatus(plan.instanceId()).orElseThrow();
            if (!plan.instanceId().equals(identity.get("processInstanceId")) || !(identity.get("tasks") instanceof List<?> metadata)) {
                return unconfirmed("TASK_IDENTITY_NOT_CONFIRMED");
            }
            if (metadata.size() > 200) return unconfirmed("TASK_IDENTITY_LIMIT_EXCEEDED");
            List<SubTableBatchPersonalOutcome.RowTask> scoped = new ArrayList<>();
            boolean identitiesComplete = true;
            for (Object value : tasks) {
                if (!(value instanceof Map<?, ?> task)) { identitiesComplete = false; continue; }
                if (!hasLiveContext(task)) { identitiesComplete = false; continue; }
                if (!plan.instanceId().equals(task.get("processInstanceId"))
                        || !plan.definitionId().equals(task.get("processDefinitionId"))
                        || plan.expected().stream().noneMatch(expected -> expected.node().equals(task.get("taskDefinitionKey")))) continue;
                if (!(task.get("taskId") instanceof String id) || id.isBlank()) { identitiesComplete = false; continue; }
                var matches = metadata.stream().filter(Map.class::isInstance).map(raw -> (Map<?, ?>) raw)
                        .filter(row -> id.equals(row.get("taskId"))).toList();
                if (matches.size() != 1) { identitiesComplete = false; continue; }
                Map<?, ?> row = matches.get(0);
                if (!hasIdentity(plan, task, row)) { identitiesComplete = false; continue; }
                boolean allowed = plan.expected().stream().anyMatch(expected -> expected.node().equals(task.get("taskDefinitionKey"))
                        && expected.node().equals(row.get("taskDefinitionKey")) && expected.table().equals(row.get("subTableName"))
                        && expected.rowKeys().stream().anyMatch(key -> sameKey(key, row.get("subTableRowKey"))));
                if (allowed) {
                    scoped.add(new SubTableBatchPersonalOutcome.RowTask(task, row.get("subTableName"), row.get("subTableRowKey")));
                }
            }
            var result = SubTableBatchPersonalOutcome.verify(plan.expected(), scoped);
            return !identitiesComplete && "VERIFIED".equals(result.status())
                    ? unconfirmed("TASK_IDENTITY_NOT_CONFIRMED") : result;
        } catch (RuntimeException exception) {
            // Both remote reads follow a committed action. Uncertainty is explicit and cannot roll it back.
            return unconfirmed("TASK_IDENTITY_QUERY_UNAVAILABLE");
        }
    }

    private static boolean hasLiveContext(Map<?, ?> task) {
        for (String field : List.of("processInstanceId", "processDefinitionId", "taskDefinitionKey")) {
            if (!(task.get(field) instanceof String value) || value.isBlank()) return false;
        }
        return true;
    }

    private static boolean hasIdentity(SubTableBatchInitialMiOutcome.Plan plan, Map<?, ?> task, Map<?, ?> row) {
        if (!task.get("taskDefinitionKey").equals(row.get("taskDefinitionKey"))
                || !(row.get("subTableName") instanceof String table) || table.isBlank()
                || !(row.get("subTableRowKey") instanceof Map<?, ?> key) || key.isEmpty()) return false;
        return plan.expected().stream().filter(expected -> expected.node().equals(task.get("taskDefinitionKey"))
                        && expected.table().equals(table))
                .allMatch(expected -> expected.rowKeys().stream().anyMatch(own -> own.keySet().equals(key.keySet())));
    }

    static boolean sameKey(Map<String, Object> expected, Object value) {
        if (!(value instanceof Map<?, ?> raw) || !expected.keySet().equals(raw.keySet())) return false;
        var actual = SubTableRowKeySupport.normalizeStringKeyMap(raw);
        var fields = new ArrayList<>(expected.keySet());
        return SubTableRowKeySupport.canonicalRowKeyString(fields, expected)
                .equals(SubTableRowKeySupport.canonicalRowKeyString(fields, actual));
    }
    private static BatchAssignmentVerification unconfirmed(String reason) {
        return new BatchAssignmentVerification("UNCONFIRMED", List.of(new BatchAssignmentVerification.Issue(null, reason)));
    }
}
