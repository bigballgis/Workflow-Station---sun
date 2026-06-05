package com.admin.exception;

/**
 * Relation Table 表名重复异常
 */
public class RelationTableNameDuplicateException extends AdminBusinessException {

    public RelationTableNameDuplicateException(String tableName) {
        super("RELATION_TABLE_NAME_DUPLICATE",
                "Table name already exists platform-wide (Relation Table or Table Design): " + tableName);
    }
}
