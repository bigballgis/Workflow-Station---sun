package com.admin.exception;

/**
 * 部署失败异常
 */
public class DeploymentFailedException extends AdminBusinessException {
    
    public DeploymentFailedException(String message) {
        super("DEPLOYMENT_FAILED", message);
    }
    
    public DeploymentFailedException(String errorCode, String message) {
        super(errorCode, message);
    }
    
    public DeploymentFailedException(String message, Throwable cause) {
        super("DEPLOYMENT_FAILED", message, cause);
    }
}
