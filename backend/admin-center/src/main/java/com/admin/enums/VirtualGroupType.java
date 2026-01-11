package com.admin.enums;

/**
 * 虚拟组类型枚举
 */
public enum VirtualGroupType {
    /** 静态组 - 手动管理成员 */
    STATIC,
    /** 动态组 - 基于规则自动计算成员 */
    DYNAMIC,
    /** 项目组 */
    PROJECT,
    /** 审批组 */
    APPROVAL,
    /** 工作组 */
    WORK,
    /** 工作组（别名） */
    WORKGROUP,
    /** 临时组 */
    TEMPORARY,
    /** 角色组 */
    ROLE,
    /** 任务处理组 */
    TASK_HANDLER
}
