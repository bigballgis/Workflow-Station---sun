package com.platform.common.exception;

import com.platform.common.enums.ErrorCode;
import lombok.Getter;

import java.util.Map;

/**
 * Base exception for all platform exceptions.
 */
@Getter
public class PlatformException extends RuntimeException {
    
    private final ErrorCode errorCode;
    private final Map<String, Object> details;
    
    public PlatformException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.details = null;
    }
    
    public PlatformException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = null;
    }
    
    public PlatformException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = null;
    }
    
    public PlatformException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }
    
    public PlatformException(ErrorCode errorCode, String message, Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = details;
    }
}
