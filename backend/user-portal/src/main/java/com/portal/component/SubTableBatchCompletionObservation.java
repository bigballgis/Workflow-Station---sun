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
            List<Object> scoped = new ArrayList<>();
            for (Object value : tasks) {
                if (!(value instanceof Map<?, ?> task) || !(task.get("taskId") instanceof String id)) continue;
                var matches = metadata.stream().filter(Map.class::isInstance).map(raw -> (Map<?, ?>) raw)
                        .filter(row -> id.equals(row.get("taskId"))).toList();
                if (matches.size() != 1) continue;
                Map<?, ?> row = matches.get(0);
                boolean allowed = plan.expected().stream().anyMatch(expected -> expected.node().equals(task.get("taskDefinitionKey"))
                        && expected.node().equals(row.get("taskDefinitionKey")) && expected.table().equals(row.get("subTableName"))
                        && expected.rowKeys().stream().anyMatch(key -> sameKey(key, row.get("subTableRowKey"))));
                if (allowed) scoped.add(value);
            }
            return SubTableBatchInitialMiOutcome.verify(plan.expected(), plan.instanceId(), plan.definitionId(),
                    () -> java.util.Optional.of(Map.of("tasks", scoped, "totalCount", scoped.size())));
        } catch (RuntimeException exception) {
            // Both remote reads follow a committed action. Uncertainty is explicit and cannot roll it back.
            return unconfirmed("TASK_IDENTITY_QUERY_UNAVAILABLE");
        }
    }

    private static boolean sameKey(Map<String, Object> expected, Object value) {
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
