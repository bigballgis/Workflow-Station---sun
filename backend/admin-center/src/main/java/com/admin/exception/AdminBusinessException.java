package com.admin.exception;

import lombok.Getter;

/**
 * 管理员中心业务异常基类
 */
@Getter
public class AdminBusinessException extends RuntimeException {
    
    private final String errorCode;
    private final String errorMessage;
    
    public AdminBusinessException(String errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
    
    public AdminBusinessException(String errorCode, String errorMessage, Throwable cause) {
        super(errorMessage, cause);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}
