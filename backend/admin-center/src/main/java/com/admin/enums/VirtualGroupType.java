package com.admin.enums;

/**
 * 虚拟组类型枚举
 * 
 * 只有两种类型：
 * - SYSTEM: 系统内置虚拟组，不可删除
 * - CUSTOM: 用户自定义虚拟组，可删除
 * 
 * 所有虚拟组都可以通过 ad_group 字段绑定 AD 组
 */
public enum VirtualGroupType {
    /** 系统内置虚拟组 - 不可删除 */
    SYSTEM,
    /** 用户自定义虚拟组 - 可删除 */
    CUSTOM
}
