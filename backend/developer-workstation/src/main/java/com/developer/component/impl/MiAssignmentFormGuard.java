package com.developer.component.impl;

import com.developer.dto.ValidationResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validates that BPMN MI assignment contracts agree with each other across nodes for
 * the same Sub Table. Whether a bound form actually places the {@code miAssignment}
 * component is the developer's own call (a form that never assigns MI subtasks has no
 * reason to carry one) — only a genuine conflict between nodes is still flagged.
 */
final class MiAssignmentFormGuard {

    private final Map<String, NodeContract> contractsBySubTable = new LinkedHashMap<>();

    void validate(
            Map<String, String> properties,
            String userTaskId,
            String subProcessId,
            ValidationResult result) {
        String mode = normalized(properties.get("assigneeMode"));
        String subTableName = normalized(properties.get("subTableName"));
        if (!isSupportedMode(mode) || subTableName == null) {
            return;
        }

        AssignmentContract contract = new AssignmentContract(
                mode,
                normalized(properties.get("assigneeField")),
                normalized(properties.get("roleField")),
                normalized(properties.get("buField")));
        NodeContract previous = contractsBySubTable.putIfAbsent(
                subTableName, new NodeContract(userTaskId, contract));
        if (previous != null && !previous.contract().equals(contract)) {
            result.addError(
                    "CONFLICTING_MI_ASSIGNMENT_CONFIG",
                    "SubTable '" + subTableName + "' has conflicting MI assignment settings on nodes "
                            + previous.nodeId() + " and " + userTaskId,
                    subProcessId);
        }
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isSupportedMode(String mode) {
        return "user".equalsIgnoreCase(mode)
                || "role".equalsIgnoreCase(mode)
                || "both".equalsIgnoreCase(mode);
    }

    private record AssignmentContract(
            String mode, String assigneeField, String roleField, String buField) {

        private AssignmentContract {
            mode = mode.toLowerCase(java.util.Locale.ROOT);
        }
    }

    private record NodeContract(String nodeId, AssignmentContract contract) {
    }
}
