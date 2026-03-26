package com.platform.common.enums;

import lombok.Getter;

/**
 * Status of a Relation Table definition.
 * INIT - newly created, never deployed.
 * DRAFT - structure modified but not yet deployed.
 * DEPLOYED - structure successfully deployed to the database.
 * UPDATED - deployed table edited, pending re-deployment.
 * ROLLBACK - structure rolled back to a previous version.
 */
@Getter
public enum RelationTableStatus {

    INIT("INIT", "Init"),
    DRAFT("DRAFT", "Draft"),
    DEPLOYED("DEPLOYED", "Deployed"),
    UPDATED("UPDATED", "Updated"),
    ROLLBACK("ROLLBACK", "Rollback");

    private final String code;
    private final String displayName;

    RelationTableStatus(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    /**
     * Get RelationTableStatus enum from code string
     */
    public static RelationTableStatus fromCode(String code) {
        if (code == null) return null;
        for (RelationTableStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }
}
