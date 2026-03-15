package com.admin.exception;

/**
 * Dashboard 未找到异常
 */
public class DashboardNotFoundException extends AdminBusinessException {

    public DashboardNotFoundException(String dashboardId) {
        super("DASHBOARD_NOT_FOUND", "Dashboard 不存在: " + dashboardId);
    }

    public DashboardNotFoundException(String message, Throwable cause) {
        super("DASHBOARD_NOT_FOUND", message, cause);
    }
}
