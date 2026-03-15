package com.admin.exception;

/**
 * 重复分配异常
 */
public class DuplicateAssignmentException extends AdminBusinessException {

    public DuplicateAssignmentException(String dashboardId, String targetType, String targetId) {
        super("DUPLICATE_ASSIGNMENT", "重复分配: dashboardId=" + dashboardId + ", targetType=" + targetType + ", targetId=" + targetId);
    }

    public DuplicateAssignmentException(String message, Throwable cause) {
        super("DUPLICATE_ASSIGNMENT", message, cause);
    }
}
