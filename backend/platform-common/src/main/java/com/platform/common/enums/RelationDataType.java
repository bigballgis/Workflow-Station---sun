package com.platform.common.enums;

import lombok.Getter;

/**
 * Supported data types for Relation Table field definitions.
 */
@Getter
public enum RelationDataType {

    VARCHAR("VARCHAR", "Variable Character"),
    INTEGER("INTEGER", "Integer"),
    BIGINT("BIGINT", "Big Integer"),
    DECIMAL("DECIMAL", "Decimal"),
    BOOLEAN("BOOLEAN", "Boolean"),
    DATE("DATE", "Date"),
    TIMESTAMP("TIMESTAMP", "Timestamp"),
    TEXT("TEXT", "Text");

    private final String code;
    private final String displayName;

    RelationDataType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    /**
     * Get RelationDataType enum from code string
     */
    public static RelationDataType fromCode(String code) {
        if (code == null) return null;
        for (RelationDataType dataType : values()) {
            if (dataType.code.equalsIgnoreCase(code)) {
                return dataType;
            }
        }
        return null;
    }
}
