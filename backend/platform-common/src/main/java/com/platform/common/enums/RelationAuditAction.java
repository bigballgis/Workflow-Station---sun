package com.platform.common.enums;

import lombok.Getter;

/**
 * Audit log action types for Relation Table data changes.
 */
@Getter
public enum RelationAuditAction {

    ADD("ADD", "Add"),
    UPDATE("UPDATE", "Update"),
    DELETE("DELETE", "Delete"),
    STATUS_CHANGE("STATUS_CHANGE", "Status Change");

    private final String code;
    private final String displayName;

    RelationAuditAction(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    /**
     * Get RelationAuditAction enum from code string
     */
    public static RelationAuditAction fromCode(String code) {
        if (code == null) return null;
        for (RelationAuditAction action : values()) {
            if (action.code.equalsIgnoreCase(code)) {
                return action;
            }
        }
        return null;
    }
}
