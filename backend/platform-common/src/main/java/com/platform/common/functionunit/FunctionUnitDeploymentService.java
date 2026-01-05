package com.platform.common.functionunit;

/**
 * Service interface for deploying function units.
 * Validates: Requirements 2.8, 14.8
 */
public interface FunctionUnitDeploymentService {
    
    /**
     * Deploy a function unit to an environment.
     * 
     * @param functionUnitId Function unit ID
     * @param environment Target environment
     * @param strategy Deployment strategy
     * @return Deployment result
     */
    DeploymentResult deploy(String functionUnitId, Environment environment, DeploymentStrategy strategy);
    
    /**
     * Rollback a deployment.
     * 
     * @param deploymentId Deployment ID
     * @return Rollback result
     */
    RollbackResult rollback(String deploymentId);
    
    /**
     * Get deployment status.
     * 
     * @param deploymentId Deployment ID
     * @return Deployment status
     */
    DeploymentStatus getStatus(String deploymentId);
}
