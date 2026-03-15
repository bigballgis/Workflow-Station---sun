package com.admin.exception;

/**
 * Dashboard 存在关联分配异常
 */
public class DashboardHasAssignmentsException extends AdminBusinessException {

    public DashboardHasAssignmentsException(String dashboardId) {
        super("DASHBOARD_HAS_ASSIGNMENTS", "Dashboard 存在关联分配，无法删除: " + dashboardId);
    }

    public DashboardHasAssignmentsException(String message, Throwable cause) {
        super("DASHBOARD_HAS_ASSIGNMENTS", message, cause);
    }
}
