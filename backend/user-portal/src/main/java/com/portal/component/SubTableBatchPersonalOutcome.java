package com.portal.component;

import com.portal.dto.BatchAssignmentVerification;
import com.portal.dto.BatchAssignmentVerification.Issue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only comparison of source-bound personal targets with uniquely joined live tasks. */
final class SubTableBatchPersonalOutcome {
    private SubTableBatchPersonalOutcome() { }
    record RowTask(Map<?, ?> live, Object table, Object rowKey) { }

    static BatchAssignmentVerification verify(List<SubTableBatchInitialMiOutcome.Expected> expected, List<RowTask> tasks) {
        List<Issue> issues = new ArrayList<>();
        Set<String> used = new HashSet<>();
        boolean complete = !expected.isEmpty();
        for (var item : expected) {
            if (!item.personalOnly() || item.people().isEmpty() || item.people().size() != item.rowKeys().size()) {
                complete = false;
                continue;
            }
            for (var target : item.people()) {
                var matches = tasks.stream().filter(task -> item.node().equals(task.live().get("taskDefinitionKey"))
                        && item.table().equals(task.table())
                        && SubTableBatchCompletionObservation.sameKey(target.rowKey(), task.rowKey())).toList();
                if (target.assignee().isBlank() || matches.size() != 1
                        || item.rowKeys().stream().filter(key -> SubTableBatchCompletionObservation.sameKey(key, target.rowKey())).count() != 1) {
                    complete = false;
                    continue;
                }
                RowTask task = matches.get(0);
                String id = (String) task.live().get("taskId");
                if (!used.add(id)) { complete = false; continue; }
                Object actual = task.live().get("currentAssignee");
                if (actual == null || actual instanceof String person && person.isBlank()) {
                    issues.add(new Issue(id, "PERSON_TASK_UNASSIGNED"));
                } else if (actual instanceof String person) {
                    if (!target.assignee().equals(person)) issues.add(new Issue(id, "PERSON_TASK_TARGET_MISMATCH"));
                } else complete = false;
            }
        }
        if (!issues.isEmpty()) return new BatchAssignmentVerification("FAILED", List.copyOf(issues));
        return complete && used.size() == tasks.size() ? new BatchAssignmentVerification("VERIFIED", List.of())
                : new BatchAssignmentVerification("UNCONFIRMED", List.of(new Issue(null, "ROW_RESULTS_NOT_VERIFIED")));
    }
}
