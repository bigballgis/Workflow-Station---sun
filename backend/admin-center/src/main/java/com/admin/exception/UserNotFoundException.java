package com.admin.exception;

/**
 * 用户未找到异常
 */
public class UserNotFoundException extends AdminBusinessException {
    
    public UserNotFoundException(String userId) {
        super("USER_NOT_FOUND", "用户不存在: " + userId);
    }
}
