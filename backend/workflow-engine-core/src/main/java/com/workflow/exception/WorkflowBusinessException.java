package com.workflow.exception;

/**
 * 工作流业务异常
 */
public class WorkflowBusinessException extends RuntimeException {
    
    private final String errorCode;
    private final Object[] parameters;
    
    public WorkflowBusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.parameters = null;
    }
    
    public WorkflowBusinessException(String errorCode, String message, Object... parameters) {
        super(message);
        this.errorCode = errorCode;
        this.parameters = parameters;
    }
    
    public WorkflowBusinessException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.parameters = null;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public Object[] getParameters() {
        return parameters;
    }
}