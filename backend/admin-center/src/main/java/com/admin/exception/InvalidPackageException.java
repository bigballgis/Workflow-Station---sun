package com.admin.exception;

/**
 * 无效功能包异常
 */
public class InvalidPackageException extends AdminBusinessException {
    
    public InvalidPackageException(String message) {
        super("INVALID_PACKAGE", message);
    }
    
    public InvalidPackageException(String errorCode, String message) {
        super(errorCode, message);
    }
}
