package com.portal.util;

import com.portal.dto.TaskInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resolves the BU + role identity of a claim-pool task for Leader/Approver matching.
 */
public final class ClaimPoolTaskIdentity {

    private ClaimPoolTaskIdentity() {
    }

    public static String businessUnit(TaskInfo task) {
        if (task == null) {
            return null;
        }
        if (task.getBpmnBusinessUnitId() != null && !task.getBpmnBusinessUnitId().isBlank()) {
            return task.getBpmnBusinessUnitId().trim();
        }
        Map<String, Object> variables = task.getVariables();
        if (variables == null) {
            return null;
        }
        Object bu = firstPresent(variables, "businessUnitId", "activeBusinessUnitId");
        return bu == null ? null : bu.toString().trim();
    }

    public static List<String> roleIds(TaskInfo task) {
        if (task == null) {
            return List.of();
        }
        if (task.getBpmnRoleIds() != null && !task.getBpmnRoleIds().isEmpty()) {
            return task.getBpmnRoleIds();
        }
        Map<String, Object> variables = task.getVariables();
        if (variables == null) {
            return List.of();
        }
        Object raw = firstPresent(variables, "roleIds", "roleId");
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    out.add(item.toString().trim());
                }
            }
            return out;
        }
        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : text.split(",")) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
    }

    private static Object firstPresent(Map<String, Object> variables, String... keys) {
        for (String key : keys) {
            Object value = variables.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value;
            }
        }
        return null;
    }
}
