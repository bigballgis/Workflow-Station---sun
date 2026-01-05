package com.admin.exception;

/**
 * 用户名已存在异常
 */
public class UsernameAlreadyExistsException extends AdminBusinessException {
    
    public UsernameAlreadyExistsException(String username) {
        super("USERNAME_EXISTS", "用户名已存在: " + username);
    }
}
