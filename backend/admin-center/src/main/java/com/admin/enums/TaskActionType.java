package com.admin.enums;

/**
 * 任务操作类型枚举
 */
public enum TaskActionType {
    /** 任务创建 */
    CREATED,
    /** 任务分配 */
    ASSIGNED,
    /** 任务认领 */
    CLAIMED,
    /** 任务委托 */
    DELEGATED,
    /** 任务完成 */
    COMPLETED,
    /** 任务取消 */
    CANCELLED,
    /** 任务退回 */
    RETURNED
}
