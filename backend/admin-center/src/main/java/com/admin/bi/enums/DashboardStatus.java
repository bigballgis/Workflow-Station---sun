package com.admin.bi.enums;

/**
 * Dashboard 状态枚举
 */
public enum DashboardStatus {
    /** 有效 */
    ACTIVE,
    /** 自动失效（同步时发现 Superset 端不再满足条件） */
    AUTO_INACTIVE,
    /** 手动失效（用户在 Admin Center 主动标记） */
    MANUAL_INACTIVE
}
