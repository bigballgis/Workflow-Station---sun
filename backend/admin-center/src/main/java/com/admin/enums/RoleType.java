package com.admin.enums;

/**
 * 角色类型枚举
 * 角色分为三大类：业务角色、管理角色、开发角色
 */
public enum RoleType {
    /** 业务角色 - 用于 User Portal 用户管理，可分配功能单元访问权限 */
    BUSINESS,
    /** 管理角色 - 用于 Admin Center 管理功能 */
    ADMIN,
    /** 开发角色 - 用于 Developer Workstation 功能权限控制 */
    DEVELOPER
}
