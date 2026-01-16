package com.platform.security.enums;

/**
 * 角色分配目标类型枚举
 * 定义角色可以分配给的目标类型
 * 
 * Note: DEPARTMENT and DEPARTMENT_HIERARCHY have been removed as part of
 * the migration from Department to BusinessUnit architecture.
 * Role assignment now uses BusinessUnit-based mechanisms through the
 * workflow engine's AssigneeType enum.
 */
public enum AssignmentTargetType {
    /**
     * 直接分配给用户
     */
    USER,
    
    /**
     * 分配给虚拟组（虚拟组的所有成员）
     */
    VIRTUAL_GROUP
}
