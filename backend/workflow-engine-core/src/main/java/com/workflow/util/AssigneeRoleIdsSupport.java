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
    /**
     * 把 {@code currentItem[roleField]} 规范成 role code 列表：支持 {@code List}、JSON 数组文本、逗号串、单值。
     */
    public static List<String> resolveRoleCodesFromItem(java.util.Map<String, Object> currentItem, String roleField) {
        Object raw = currentItem.get(roleField);
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<String> out = new java.util.ArrayList<>();
            for (Object o : list) {
                if (o != null && !String.valueOf(o).trim().isEmpty()) {
                    out.add(String.valueOf(o).trim());
                }
            }
            return out;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return List.of();
        }
        // AssigneeRoleIdsSupport.parseRoleIds 按逗号切分并去重去空；JSON 数组文本先剥括号引号。
        String normalized = text.replaceAll("^[\\[\\]\"']+|[\\[\\]\"']+$", "").replace("\"", "").replace("'", "");
        return AssigneeRoleIdsSupport.parseRoleIds(normalized, null);
    }

}
