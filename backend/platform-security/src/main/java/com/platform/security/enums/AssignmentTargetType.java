package com.platform.security.enums;

/**
 * 角色分配目标类型枚举
 * 定义角色可以分配给的四种目标类型
 */
public enum AssignmentTargetType {
    /**
     * 直接分配给用户
     */
    USER,
    
    /**
     * 分配给部门（仅该部门的用户）
     */
    DEPARTMENT,
    
    /**
     * 分配给部门层级（该部门及所有下级部门的用户）
     */
    DEPARTMENT_HIERARCHY,
    
    /**
     * 分配给虚拟组（虚拟组的所有成员）
     */
    VIRTUAL_GROUP
}
