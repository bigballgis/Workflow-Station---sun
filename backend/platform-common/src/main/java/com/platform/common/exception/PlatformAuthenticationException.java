package com.platform.common.exception;

import com.platform.common.enums.ErrorCode;

/**
 * Exception for authentication failures.
 */
public class PlatformAuthenticationException extends PlatformException {
    
    public PlatformAuthenticationException() {
        super(ErrorCode.AUTH_TOKEN_INVALID);
    }
    
    public PlatformAuthenticationException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public PlatformAuthenticationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public PlatformAuthenticationException(String message) {
        super(ErrorCode.AUTH_TOKEN_INVALID, message);
    }
    
    public PlatformAuthenticationException(String message, Throwable cause) {
        super(ErrorCode.AUTH_TOKEN_INVALID, message, cause);
    }
}
