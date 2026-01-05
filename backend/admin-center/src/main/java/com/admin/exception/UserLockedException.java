package com.admin.exception;

/**
 * 用户已锁定异常
 */
public class UserLockedException extends AdminBusinessException {
    
    public UserLockedException(String userId) {
        super("USER_LOCKED", "用户已被锁定: " + userId);
    }
}
