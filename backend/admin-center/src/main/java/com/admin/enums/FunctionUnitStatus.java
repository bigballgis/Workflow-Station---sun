package com.admin.enums;

/**
 * 功能单元状态枚举
 */
public enum FunctionUnitStatus {
    /**
     * 草稿 - 刚导入，未验证
     */
    DRAFT,
    
    /**
     * 已验证 - 通过验证，可以部署
     */
    VALIDATED,
    
    /**
     * 已部署 - 已部署到某个环境
     */
    DEPLOYED,
    
    /**
     * 已废弃 - 不再使用
     */
    DEPRECATED
}
