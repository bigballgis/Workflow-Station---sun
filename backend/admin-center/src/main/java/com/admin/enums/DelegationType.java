package com.admin.enums;

/**
 * 权限委托类型枚举
 */
public enum DelegationType {
    /**
     * 临时委托 - 有时间限制的权限委托
     */
    TEMPORARY,
    
    /**
     * 代理委托 - 代理人可以行使权限，但原权限人仍保留权限
     */
    PROXY,
    
    /**
     * 转移委托 - 权限完全转移给受委托人，原权限人失去权限
     */
    TRANSFER
}