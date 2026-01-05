package com.admin.exception;

/**
 * 权限拒绝异常
 */
public class PermissionDeniedException extends AdminBusinessException {
    
    public PermissionDeniedException(String message) {
        super("PERMISSION_DENIED", message);
    }
    
    public PermissionDeniedException(String userId, String resource, String action) {
        super("PERMISSION_DENIED", 
              String.format("用户 %s 没有对资源 %s 执行 %s 操作的权限", userId, resource, action));
    }
}
