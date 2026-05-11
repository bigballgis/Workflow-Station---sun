package com.platform.common.jdbc;

/**
 * Raised when a physical table has no PRIMARY KEY in the current PostgreSQL schema.
 */
public class MissingPhysicalTablePrimaryKeyException extends IllegalStateException {

    private final String physicalTableName;

    public MissingPhysicalTablePrimaryKeyException(String physicalTableName) {
        super("Physical table has no primary key in current_schema: " + physicalTableName);
        this.physicalTableName = physicalTableName;
    }

    public String getPhysicalTableName() {
        return physicalTableName;
    }
}
