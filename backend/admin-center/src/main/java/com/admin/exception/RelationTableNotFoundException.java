package com.admin.exception;

/**
 * Relation Table 定义不存在异常
 */
public class RelationTableNotFoundException extends AdminBusinessException {

    public RelationTableNotFoundException(Long tableId) {
        super("RELATION_TABLE_NOT_FOUND", "Relation Table 不存在: " + tableId);
    }

    public RelationTableNotFoundException(String tableName) {
        super("RELATION_TABLE_NOT_FOUND", "Relation Table 不存在: " + tableName);
    }
}
