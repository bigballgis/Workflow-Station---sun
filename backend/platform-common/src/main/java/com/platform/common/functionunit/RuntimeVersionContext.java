package com.platform.common.functionunit;

import java.util.List;
import java.util.Map;

/** Server-owned association; a DW row id is deliberately not a release identity. */
public record RuntimeVersionContext(String catalogId, String code, String versionLabel,
                                    String processDefinitionId) {
    private static final List<String> RESERVED = List.of("functionUnitId", "functionUnitCatalogId",
            "functionUnitCode", "functionUnitVersionLabel", "functionUnitVersionId",
            "processDefinitionId", "expectedProcessDefinitionId");

    public RuntimeVersionContext {
        if (catalogId == null || catalogId.isBlank() || code == null || code.isBlank()) {
            throw new FunctionUnitVersionException("VERSION_RECOVERY_REQUIRED", catalogId);
        }
    }

    public static void rejectClientMetadata(Map<String, ?> payload) {
        if (payload == null) return;
        for (String key : RESERVED) {
            if (payload.containsKey(key)) {
                throw new FunctionUnitVersionException("VERSION_CONTEXT_MODIFICATION_DENIED", key);
            }
        }
    }

    public static List<String> metadataKeys() { return RESERVED; }

    public static void requireUnchanged(Map<String, ?> existing, Map<String, ?> incoming) {
        if (incoming == null) return;
        for (String key : RESERVED) {
            if (incoming.containsKey(key) && !java.util.Objects.equals(existing.get(key), incoming.get(key))) {
                throw new FunctionUnitVersionException("VERSION_CONTEXT_MODIFICATION_DENIED", key);
            }
        }
    }

    public void applyTo(Map<String, Object> variables) {
        requireMatch(variables, "functionUnitId", catalogId);
        requireMatch(variables, "functionUnitCode", code);
        if (versionLabel != null) requireMatch(variables, "functionUnitVersionLabel", versionLabel);
        variables.put("functionUnitId", catalogId);
        variables.put("functionUnitCode", code);
        if (versionLabel != null) variables.put("functionUnitVersionLabel", versionLabel);
    }

    private void requireMatch(Map<String, Object> variables, String key, String expected) {
        Object value = variables.get(key);
        if (value != null && !expected.equals(String.valueOf(value))) {
            throw new FunctionUnitVersionException("VERSION_CONTEXT_MISMATCH", catalogId);
        }
    }
}
