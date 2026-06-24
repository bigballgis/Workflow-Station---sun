package com.workflow.util;

import java.util.Arrays;
import java.util.List;

/**
 * Parses BPMN extension {@code roleIds} (comma-separated) with legacy {@code roleId} fallback.
 */
public final class AssigneeRoleIdsSupport {

    private AssigneeRoleIdsSupport() {
    }

    public static List<String> parseRoleIds(String roleIdsRaw, String legacyRoleId) {
        if (roleIdsRaw != null && !roleIdsRaw.isBlank()) {
            return Arrays.stream(roleIdsRaw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();
        }
        if (legacyRoleId != null && !legacyRoleId.isBlank()) {
            return List.of(legacyRoleId.trim());
        }
        return List.of();
    }
}
