package com.portal.component;

import com.portal.dto.BatchAssignmentVerification.Issue;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compares actual Flowable candidate links, not cached assignment-target strings. */
final class SubTableBatchRoleOutcome {
    private SubTableBatchRoleOutcome() { }

    static boolean verify(SubTableBatchInitialMiOutcome.Expected item, List<SubTableBatchPersonalOutcome.RowTask> tasks,
                          Set<String> used, List<Issue> issues) {
        boolean complete = true;
        for (var target : item.roles()) {
            var matches = SubTableBatchPersonalOutcome.matching(item, target.rowKey(), tasks);
            if (target.members().isEmpty() || matches.size() != 1
                    || item.rowKeys().stream().filter(key -> SubTableBatchCompletionObservation.sameKey(key, target.rowKey())).count() != 1) {
                complete = false;
                continue;
            }
            var task = matches.get(0).live();
            if (!used.add((String) task.get("taskId"))) { complete = false; continue; }
            complete &= inspect(target, task, issues);
        }
        return complete;
    }

    private static boolean inspect(SubTableBatchInitialMiOutcome.RoleTarget target, Map<?, ?> task, List<Issue> issues) {
        if (!task.containsKey("currentAssignee") || task.get("currentAssignee") != null
                && !(task.get("currentAssignee") instanceof String person && person.isBlank())) return false;
        for (String field : List.of("miAssigneeMode", "miRoleCode", "miBusinessUnitCode", "assignmentType")) {
            if (!(task.get(field) instanceof String value) || value.isBlank()) return false;
        }
        String id = (String) task.get("taskId");
        if (!"role".equals(task.get("miAssigneeMode")) || !target.roleCode().equals(task.get("miRoleCode"))
                || !target.businessUnitCode().equals(task.get("miBusinessUnitCode"))) {
            issues.add(new Issue(id, "ROLE_TASK_CONTEXT_MISMATCH"));
            return true;
        }
        if (!task.containsKey("candidateUserIds") || !task.containsKey("candidateGroupIds")) return false;
        var users = ids(task.get("candidateUserIds"));
        var groups = ids(task.get("candidateGroupIds"));
        if (users == null || groups == null) return false;
        if (!target.members().equals(users) || !groups.isEmpty()) {
            issues.add(new Issue(id, "ROLE_TASK_CANDIDATES_MISMATCH"));
        } else if (!"CANDIDATE_USERS".equals(task.get("assignmentType"))) return false;
        return true;
    }

    private static Set<String> ids(Object value) {
        // TaskListResult's live converter explicitly serializes null for an empty identity-link list.
        if (value == null) return Set.of();
        if (!(value instanceof List<?> values)) return null;
        Set<String> ids = new HashSet<>();
        for (Object raw : values) {
            if (!(raw instanceof String id) || id.isBlank() || !id.equals(id.trim()) || !ids.add(id)) return null;
        }
        return Set.copyOf(ids);
    }
}
