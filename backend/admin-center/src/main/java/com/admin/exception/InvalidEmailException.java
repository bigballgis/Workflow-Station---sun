package com.admin.exception;

/**
 * 无效邮箱异常
 */
public class InvalidEmailException extends AdminBusinessException {
    
    public InvalidEmailException(String email) {
        super("INVALID_EMAIL", "邮箱格式无效: " + email);
    }
}
