package com.workflow.exception;

/**
 * 工作流系统异常
 */
public class WorkflowSystemException extends RuntimeException {
    
    private final String errorCode;
    
    public WorkflowSystemException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public WorkflowSystemException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}