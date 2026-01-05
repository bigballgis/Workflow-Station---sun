package com.admin.enums;

/**
 * 数据范围类型
 */
public enum DataScopeType {
    /** 全部数据 */
    ALL,
    /** 本部门数据 */
    DEPARTMENT,
    /** 本部门及子部门数据 */
    DEPARTMENT_AND_CHILDREN,
    /** 仅本人数据 */
    SELF,
    /** 自定义 */
    CUSTOM
}
