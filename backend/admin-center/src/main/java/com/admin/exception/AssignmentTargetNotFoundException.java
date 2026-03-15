package com.admin.exception;

/**
 * 分配目标不存在异常
 */
public class AssignmentTargetNotFoundException extends AdminBusinessException {

    public AssignmentTargetNotFoundException(String targetType, String targetId) {
        super("ASSIGNMENT_TARGET_NOT_FOUND", "分配目标不存在: targetType=" + targetType + ", targetId=" + targetId);
    }

    public AssignmentTargetNotFoundException(String message, Throwable cause) {
        super("ASSIGNMENT_TARGET_NOT_FOUND", message, cause);
    }
}
