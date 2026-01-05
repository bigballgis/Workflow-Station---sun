package com.admin.enums;

/**
 * 角色类型枚举
 */
public enum RoleType {
    /** 系统角色 - 系统内置，不可删除 */
    SYSTEM,
    /** 业务角色 - 业务相关角色 */
    BUSINESS,
    /** 功能角色 - 功能权限角色 */
    FUNCTIONAL,
    /** 临时角色 - 临时授权角色 */
    TEMPORARY
}
