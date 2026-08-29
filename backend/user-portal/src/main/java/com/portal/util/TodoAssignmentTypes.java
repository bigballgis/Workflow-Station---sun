package com.portal.util;

import com.portal.dto.TaskInfo;

import java.util.List;
import java.util.Locale;

/**
 * Toolbar / column Assignment Type values after Tasks to Claim merged into To Do.
 * {@code BU_ROLE} means a claim-pool row ({@link TaskInfo#isClaimPoolTask()}), not the engine
 * {@code assignmentType} string (often {@code VIRTUAL_GROUP}).
 */
public final class TodoAssignmentTypes {

    public static final String BU_ROLE = "BU_ROLE";
    public static final String DELEGATED = "DELEGATED";

    private TodoAssignmentTypes() {
    }

    public static boolean matches(TaskInfo task, List<String> selectedTypes) {
        if (selectedTypes == null || selectedTypes.isEmpty()) {
            return true;
        }
        List<String> types = selectedTypes.stream()
                .filter(s -> s != null && !s.isBlank())
                .toList();
        if (types.isEmpty()) {
            return true;
        }
        for (String type : types) {
            if (matchesOne(task, type)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matchesOne(TaskInfo task, String expected) {
        if (task == null || expected == null || expected.isBlank()) {
            return false;
        }
        if (BU_ROLE.equalsIgnoreCase(expected.trim())) {
            return task.isClaimPoolTask();
        }
        String actual = task.getAssignmentType();
        if (actual == null) {
            return false;
        }
        return actual.equalsIgnoreCase(expected.trim());
    }

    public static String displayToken(TaskInfo task) {
        if (task != null && task.isClaimPoolTask()) {
            return BU_ROLE;
        }
        String actual = task != null ? task.getAssignmentType() : null;
        return actual == null ? "" : actual.toUpperCase(Locale.ROOT);
    }
}
