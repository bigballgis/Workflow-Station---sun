package com.portal.enums;

/**
 * 权限申请类型枚举
 */
public enum PermissionRequestType {
    /** 角色分配申请 - 申请某个组织单元的业务角色 */
    ROLE_ASSIGNMENT,
    /** 虚拟组加入申请 - 申请加入虚拟组 */
    VIRTUAL_GROUP_JOIN,
    /** 业务单元加入申请 - 申请加入业务单元 */
    BUSINESS_UNIT_JOIN,
    
    // ========== 以下为旧类型，已废弃 ==========
    /** @deprecated 使用 ROLE_ASSIGNMENT 替代 */
    @Deprecated
    FUNCTION,
    /** @deprecated 使用 ROLE_ASSIGNMENT 替代 */
    @Deprecated
    DATA,
    /** @deprecated 使用 ROLE_ASSIGNMENT 替代 */
    @Deprecated
    TEMPORARY
}
