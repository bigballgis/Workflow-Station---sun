package com.portal.component;

import com.portal.dto.BatchAssignmentVerification;
import com.portal.dto.BatchAssignmentVerification.Issue;
import org.w3c.dom.Element;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Observes only initial-assignment collections already built by a strict source-bound preflight. */
public final class SubTableBatchInitialMiOutcome {
    private SubTableBatchInitialMiOutcome() { }
    record Expected(String node, boolean personalOnly, String table, List<Map<String, Object>> rowKeys) { }
    /** Server-only plan returned across the completion transaction, never serialized as a response. */
    public record Plan(String instanceId, String definitionId, List<Expected> expected) { }

    static List<Expected> plan(Element target, Map<String, Object> controls, List<?> items) {
        List<Expected> expected = new ArrayList<>();
        for (Object raw : controls.values()) {
            Map<?, ?> control = (Map<?, ?>) raw;
            if (!Boolean.TRUE.equals(((Map<?, ?>) control.get("capabilities")).get("allowBatchAssign"))) continue;
            Map<?, ?> config = (Map<?, ?>) control.get("assignmentConfig");
            String node = (String) config.get("taskDefinitionKey");
            if (!containsTask(target, node) || items.isEmpty()) continue;
            Object personField = config.get("assigneeField");
            boolean personalOnly = personField != null && items.stream()
                    .allMatch(value -> ((Map<?, ?>) value).get(personField) instanceof String);
            String table = (String) ((Map<?, ?>) ((Map<?, ?>) control.get("rowDesign")).get("binding")).get("tableName");
            List<Map<String, Object>> keys = new ArrayList<>();
            for (Object rawItem : items) {
                var key = new java.util.LinkedHashMap<String, Object>();
                ((Map<?, ?>) ((Map<?, ?>) rawItem).get("rowKey")).forEach((name, value) -> key.put((String) name, value));
                keys.add(Map.copyOf(key));
            }
            Expected candidate = new Expected(node, personalOnly, table, List.copyOf(keys));
            if (!expected.contains(candidate)) expected.add(candidate);
        }
        return expected;
    }

    private static boolean containsTask(Element target, String id) {
        var tasks = target.getElementsByTagNameNS("*", "userTask");
        for (int index = 0; index < tasks.getLength(); index++) {
            if (id.equals(((Element) tasks.item(index)).getAttribute("id"))) return true;
        }
        return false;
    }

    static BatchAssignmentVerification verify(List<Expected> expected, String instanceId, String definitionId,
            Supplier<Optional<Map<String, Object>>> query) {
        if (expected.isEmpty()) return null;
        try {
            Map<String, Object> result = query.get().orElseThrow();
            if (!(result.get("tasks") instanceof List<?> tasks) || !(result.get("totalCount") instanceof Number count)
                    || count.longValue() != tasks.size()) return unconfirmed("INCOMPLETE_TASK_QUERY");
            List<Issue> issues = new ArrayList<>();
            for (Object value : tasks) {
                if (!(value instanceof Map<?, ?> task) || !(task.get("taskId") instanceof String id) || id.isBlank()
                        || !instanceId.equals(task.get("processInstanceId")) || !definitionId.equals(task.get("processDefinitionId"))) continue;
                boolean personalNode = expected.stream().anyMatch(item -> item.personalOnly() && item.node().equals(task.get("taskDefinitionKey")));
                if (personalNode && (task.get("currentAssignee") == null
                        || task.get("currentAssignee") instanceof String assignee && assignee.isBlank())) {
                    issues.add(new Issue(id, "PERSON_TASK_UNASSIGNED"));
                }
            }
            // A lightweight list has no authoritative row identity: populated assignees do not prove correct row targets.
            return issues.isEmpty() ? unconfirmed("ROW_RESULTS_NOT_VERIFIED")
                    : new BatchAssignmentVerification("FAILED", List.copyOf(issues));
        } catch (RuntimeException exception) {
            // The engine has already advanced: report uncertainty, never roll back or retry its action.
            return unconfirmed("TASK_QUERY_UNAVAILABLE");
        }
    }

    private static BatchAssignmentVerification unconfirmed(String reason) {
        return new BatchAssignmentVerification("UNCONFIRMED", List.of(new Issue(null, reason)));
    }
}
