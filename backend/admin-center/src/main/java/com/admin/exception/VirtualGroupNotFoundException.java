package com.admin.exception;

/**
 * 虚拟组未找到异常
 */
public class VirtualGroupNotFoundException extends AdminBusinessException {
    
    public VirtualGroupNotFoundException(String groupId) {
        super("VIRTUAL_GROUP_NOT_FOUND", "虚拟组不存在: " + groupId);
    }
}
