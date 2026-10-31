package com.platform.common.assignment;

import java.util.List;

/** Portable node policy. Runtime identities are bound by deployment, never supplied in this JSON. */
public record AssignmentExclusionPolicy(int schemaVersion, boolean excludeInitiator,
        boolean excludePreviousHandlers, boolean excludeSelectedNodeHandlers,
        List<String> selectedNodeKeys, Backup backup) {
    /** Whether this node adds a responsibility veto to its existing assignment. */
    public boolean enabled() {
        return excludeInitiator || excludePreviousHandlers || excludeSelectedNodeHandlers;
    }

    /** Explicit fallback source, evaluated only when the primary pool was excluded in full. */
    public record Backup(boolean enabled, Mode mode, BuSource buSource,
            String businessUnitCode, List<String> roleCodes, List<String> userRefs) { }

    public enum Mode { BU_ROLE, USERS }
    public enum BuSource { CASE_START_BU, NODE_ASSIGNMENT_BU, FIXED_BU }
}
