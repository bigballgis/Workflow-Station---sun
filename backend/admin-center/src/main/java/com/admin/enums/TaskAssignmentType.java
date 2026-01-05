package com.admin.enums;

/**
 * 任务分配类型枚举
 */
public enum TaskAssignmentType {
    /** 直接分配给用户 */
    USER,
    /** 分配给虚拟组 */
    VIRTUAL_GROUP,
    /** 分配给部门+角色组合 */
    DEPARTMENT_ROLE
}
