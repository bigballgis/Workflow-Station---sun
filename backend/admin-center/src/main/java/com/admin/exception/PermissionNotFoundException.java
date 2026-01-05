package com.admin.exception;

/**
 * 权限不存在异常
 */
public class PermissionNotFoundException extends AdminBusinessException {
    
    public PermissionNotFoundException(String permissionId) {
        super("PERMISSION_NOT_FOUND", "权限不存在: " + permissionId);
    }
}