package com.admin.enums;

/**
 * 权限申请类型枚举
 */
public enum PermissionRequestType {
    /**
     * 虚拟组申请
     * 用户申请加入虚拟组，获取虚拟组绑定的角色
     */
    VIRTUAL_GROUP,
    
    /**
     * 业务单元申请
     * 用户申请加入业务单元，激活其 BU-Bounded 角色
     * 注意：用户只能申请与其 BU-Bounded 角色关联的业务单元
     */
    BUSINESS_UNIT,
    
    /**
     * @deprecated 使用 {@link #BUSINESS_UNIT} 代替
     * 保留用于向后兼容旧数据
     */
    @Deprecated
    BUSINESS_UNIT_ROLE
}
