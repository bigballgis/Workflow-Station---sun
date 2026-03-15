package com.admin.exception;

/**
 * Dashboard 已失效异常
 */
public class DashboardInactiveException extends AdminBusinessException {

    public DashboardInactiveException(String dashboardId) {
        super("DASHBOARD_INACTIVE", "Dashboard 已失效: " + dashboardId);
    }

    public DashboardInactiveException(String message, Throwable cause) {
        super("DASHBOARD_INACTIVE", message, cause);
    }
}
