package com.portal.enums;

/**
 * 变更历史类型枚举
 */
public enum ChangeType {
    /** 字段更新 */
    FIELD_UPDATE,
    /** 子表行新增 */
    SUB_TABLE_ROW_ADD,
    /** 子表行更新 */
    SUB_TABLE_ROW_UPDATE,
    /** 子表行删除 */
    SUB_TABLE_ROW_DELETE,
    /** 流程发起 */
    PROCESS_INITIATION,
    /** Record Note 新增（评论 / 附件） */
    RECORD_NOTE_ADD,
    /** Record Note 编辑 */
    RECORD_NOTE_UPDATE,
    /** Record Note 删除 */
    RECORD_NOTE_DELETE
}
