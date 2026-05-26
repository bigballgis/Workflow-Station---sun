package com.portal.service;

import com.portal.dto.TaskInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Normalizes engine task assignee state into portal {@code up_process_instance} columns:
 * single {@code current_assignee} (user id) or {@code candidate_users} (comma-separated user ids).
 */
public final class ProcessAssigneeSnapshot {

    private final String assigneeUserId;
    private final String candidateUserIds;

    public ProcessAssigneeSnapshot(String assigneeUserId, String candidateUserIds) {
        this.assigneeUserId = blankToNull(assigneeUserId);
        this.candidateUserIds = blankToNull(candidateUserIds);
    }

    public static ProcessAssigneeSnapshot empty() {
        return new ProcessAssigneeSnapshot(null, null);
    }

    public String getAssigneeUserId() {
        return assigneeUserId;
    }

    public String getCandidateUserIds() {
        return candidateUserIds;
    }

    public static ProcessAssigneeSnapshot fromEngineTask(Map<String, Object> task) {
        if (task == null || task.isEmpty()) {
            return empty();
        }
        String assignee = stringField(task.get("currentAssignee"));
        List<String> candidateIds = parseStringIdList(task.get("candidateUserIds"));
        String assignmentTarget = stringField(task.get("assignmentTarget"));
        String assignmentType = stringField(task.get("assignmentType"));

        if (assignee != null && !assignee.isBlank() && !assignee.contains(",")) {
            return new ProcessAssigneeSnapshot(assignee, null);
        }
        if (!candidateIds.isEmpty()) {
            return new ProcessAssigneeSnapshot(null, joinIds(candidateIds));
        }
        if (assignmentTarget != null) {
            if (isCandidateUserPoolType(assignmentType) || assignmentTarget.contains(",")) {
                return new ProcessAssigneeSnapshot(null, assignmentTarget);
            }
            return new ProcessAssigneeSnapshot(assignmentTarget, null);
        }
        return empty();
    }

    public static ProcessAssigneeSnapshot fromTaskInfo(TaskInfo task) {
        if (task == null) {
            return empty();
        }
        String assignee = blankToNull(task.getAssignee());
        if (assignee != null && !assignee.isBlank() && !assignee.contains(",")) {
            return new ProcessAssigneeSnapshot(assignee, null);
        }
        List<String> candidateIds = task.getCandidateUserIds();
        if (candidateIds != null && !candidateIds.isEmpty()) {
            return new ProcessAssigneeSnapshot(null, joinIds(candidateIds));
        }
        String assignmentTarget = blankToNull(task.getAssignmentTarget());
        if (assignmentTarget != null) {
            if (isCandidateUserPoolType(task.getAssignmentType()) || assignmentTarget.contains(",")) {
                return new ProcessAssigneeSnapshot(null, assignmentTarget);
            }
            return new ProcessAssigneeSnapshot(assignmentTarget, null);
        }
        return empty();
    }

    public static List<String> parseDelimitedUserKeys(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Set<String> ordered = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            if (part == null) {
                continue;
            }
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                ordered.add(trimmed);
            }
        }
        return List.copyOf(ordered);
    }

    public static Set<String> collectUserKeys(String assigneeUserId, String candidateUserIds) {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(parseDelimitedUserKeys(assigneeUserId));
        keys.addAll(parseDelimitedUserKeys(candidateUserIds));
        return keys;
    }

    private static boolean isCandidateUserPoolType(String assignmentType) {
        if (assignmentType == null || assignmentType.isBlank()) {
            return false;
        }
        String normalized = assignmentType.trim().toUpperCase();
        return "CANDIDATE_USERS".equals(normalized)
                || "BU_ROLE".equals(normalized)
                || "DEPT_ROLE".equals(normalized);
    }

    private static List<String> parseStringIdList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof Collection<?> collection) {
            List<String> out = new ArrayList<>();
            for (Object item : collection) {
                if (item == null) {
                    continue;
                }
                String s = item.toString().trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
            return out;
        }
        return parseDelimitedUserKeys(raw.toString());
    }

    private static String joinIds(Collection<String> ids) {
        return ids.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(","));
    }

    private static String stringField(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
