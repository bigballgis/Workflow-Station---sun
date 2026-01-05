package com.admin.exception;

/**
 * 部署记录未找到异常
 */
public class DeploymentNotFoundException extends AdminBusinessException {
    
    public DeploymentNotFoundException(String deploymentId) {
        super("DEPLOYMENT_NOT_FOUND", "部署记录不存在: " + deploymentId);
    }
}
