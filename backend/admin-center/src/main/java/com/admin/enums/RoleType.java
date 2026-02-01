package com.admin.enums;

/**
 * 角色类型枚举
 * 角色分为四大类：业务单元绑定型角色、业务单元无关型角色、管理角色、开发角色
 */
public enum RoleType {
    /** 业务角色 - 业务单元绑定型，需要配合业务单元使用才能生效 */
    BU_BOUNDED,
    /** 业务角色 - 业务单元无关型，用户获得后直接拥有权限，无需关联业务单元 */
    BU_UNBOUNDED,
    /** 管理角色 - 用于 Admin Center 管理功能 */
    ADMIN,
    /** 开发角色 - 用于 Developer Workstation 功能权限控制 */
    DEVELOPER;
    
    /**
     * 检查是否是业务角色类型（BU_BOUNDED 或 BU_UNBOUNDED）
     */
    public boolean isBusinessRole() {
        return this == BU_BOUNDED || this == BU_UNBOUNDED;
    }
}
