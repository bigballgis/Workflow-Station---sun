package com.portal.util;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * Determines whether the portal should auto-complete the first user task after process start.
 * Only true initiator / start-form nodes are auto-completed; approval nodes (BU_ROLE, etc.) stay open.
 */
public final class BpmnInitiatorTaskDetection {

    private BpmnInitiatorTaskDetection() {
    }

    /**
     * @param engineTask map from workflow-engine {@code TaskInfo} (e.g. {@code bpmnAssigneeType}, {@code initiatorId})
     */
    public static boolean shouldAutoCompleteFirstTask(Map<String, Object> engineTask) {
        if (engineTask == null || engineTask.isEmpty()) {
            return false;
        }

        String bpmnAssigneeType = stringField(engineTask.get("bpmnAssigneeType"));
        if (bpmnAssigneeType != null) {
            return isInitiatorAssigneeType(bpmnAssigneeType);
        }

        if (hasCandidatePool(engineTask)) {
            return false;
        }

        String assignmentType = stringField(engineTask.get("assignmentType"));
        if (assignmentType != null && isNonInitiatorAssignmentType(assignmentType)) {
            return false;
        }

        String initiatorId = stringField(engineTask.get("initiatorId"));
        if (initiatorId == null) {
            return false;
        }

        String currentAssignee = stringField(engineTask.get("currentAssignee"));
        String assignmentTarget = stringField(engineTask.get("assignmentTarget"));
        return initiatorId.equals(currentAssignee) || initiatorId.equals(assignmentTarget);
    }

    private static boolean hasCandidatePool(Map<String, Object> engineTask) {
        Object candidateUserIds = engineTask.get("candidateUserIds");
        if (candidateUserIds instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (candidateUserIds instanceof String s && !s.isBlank()) {
            return true;
        }
        Object candidateGroupIds = engineTask.get("candidateGroupIds");
        if (candidateGroupIds instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        return candidateGroupIds instanceof String s && !s.isBlank();
    }

    private static boolean isNonInitiatorAssignmentType(String assignmentType) {
        String u = assignmentType.trim().toUpperCase(Locale.ROOT);
        return "BU_ROLE".equals(u)
                || "DEPT_ROLE".equals(u)
                || "CANDIDATE_USERS".equals(u)
                || "VIRTUAL_GROUP".equals(u)
                || "CANDIDATE_GROUPS".equals(u);
    }

    private static boolean isInitiatorAssigneeType(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        return "INITIATOR".equals(u) || "PROCESS_INITIATOR".equals(u);
    }

    private static String stringField(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
