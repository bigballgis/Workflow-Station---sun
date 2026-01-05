package com.admin.enums;

/**
 * 部署策略枚举
 */
public enum DeploymentStrategy {
    /**
     * 全量部署
     */
    FULL,
    
    /**
     * 增量部署
     */
    INCREMENTAL,
    
    /**
     * 灰度部署
     */
    CANARY,
    
    /**
     * 蓝绿部署
     */
    BLUE_GREEN
}
