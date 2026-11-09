package com.admin.enums;

/**
 * 角色类型枚举
 * 业务角色一律绑定业务单元（BU + Role）；不带 BU 的只有系统自带的管理、审计、开发角色（平台访问模型 D-14 / D-64）
 */
public enum RoleType {
    /** 业务角色 - 业务单元绑定型，需要配合业务单元使用才能生效 */
    BU_BOUNDED,
    /** 管理角色 - 用于 Admin Center 管理功能 */
    ADMIN,
    /** 审计角色 - 用于 Admin Center / DW 只读查看，不授予管理写权限 */
    AUDITOR,
    /** 开发角色 - 用于 Developer Workstation 功能权限控制 */
    DEVELOPER;
    
    /**
     * 检查是否是业务角色类型（BU_BOUNDED）
     */
    public boolean isBusinessRole() {
        return this == BU_BOUNDED;
    }
}
