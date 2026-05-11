package com.platform.common.jdbc;

/**
 * Raised when a physical table has no PRIMARY KEY in the current PostgreSQL schema.
 */
public class MissingPhysicalTablePrimaryKeyException extends IllegalStateException {

    private final String physicalTableName;

    public MissingPhysicalTablePrimaryKeyException(String physicalTableName) {
        super("Physical table has no PRIMARY KEY in PostgreSQL information_schema, "
                + "and no primary-key fields in dw_field_definitions for dw_table_definitions.table_name='"
                + physicalTableName + "' (same schema): " + physicalTableName);
        this.physicalTableName = physicalTableName;
    }

    public String getPhysicalTableName() {
        return physicalTableName;
    }
}
