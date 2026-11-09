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
    RECORD_NOTE_DELETE,
    /** 用户认领 BU Role 池任务 */
    CLAIM,
    /** 持有人释放认领 */
    UNCLAIM,
    /** 他人释放认领：Leader / BU Approver，办理人失去准入时同池成员；系统在办理人失去该 BU + Role 时释放（reason） */
    FORCE_UNCLAIM,
    /** 将池任务指定给同池成员（含接手：指定给自己）：Leader / BU Approver，办理人失去准入时同池成员 */
    REASSIGN,
    /** 系统在办理人离职（账号停用或删除）后把任务指派给其 Entity / Function Manager（D-42，reason = ASSIGNEE_DEPARTED） */
    MANAGER_REASSIGN,
    /** 任务换持有人时，原持有人的单次委托随之结束（D-59，reason = HOLDER_CHANGED；fieldName = delegated_to） */
    DELEGATION_ENDED
}
