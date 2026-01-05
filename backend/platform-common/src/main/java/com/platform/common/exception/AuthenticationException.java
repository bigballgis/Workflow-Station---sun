package com.platform.common.exception;

import com.platform.common.enums.ErrorCode;

/**
 * Exception for authentication failures.
 */
public class AuthenticationException extends PlatformException {
    
    public AuthenticationException() {
        super(ErrorCode.AUTH_TOKEN_INVALID);
    }
    
    public AuthenticationException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public AuthenticationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public AuthenticationException(String message) {
        super(ErrorCode.AUTH_TOKEN_INVALID, message);
    }
    
    public AuthenticationException(String message, Throwable cause) {
        super(ErrorCode.AUTH_TOKEN_INVALID, message, cause);
    }
}
