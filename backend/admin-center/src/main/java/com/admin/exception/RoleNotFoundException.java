package com.admin.exception;

/**
 * 角色未找到异常
 */
public class RoleNotFoundException extends AdminBusinessException {
    
    public RoleNotFoundException(String roleId) {
        super("ROLE_NOT_FOUND", "角色不存在: " + roleId);
    }
}
