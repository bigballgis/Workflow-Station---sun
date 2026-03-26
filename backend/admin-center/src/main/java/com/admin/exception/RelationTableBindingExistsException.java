package com.admin.exception;

/**
 * Relation Table 存在绑定关系异常（删除已被绑定的表时抛出）
 */
public class RelationTableBindingExistsException extends AdminBusinessException {

    public RelationTableBindingExistsException(Long tableId) {
        super("RELATION_TABLE_BINDING_EXISTS", "Relation Table 存在绑定关系，无法删除: " + tableId);
    }
}
