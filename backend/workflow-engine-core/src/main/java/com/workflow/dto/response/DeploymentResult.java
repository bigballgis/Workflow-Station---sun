package com.workflow.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程部署结果
 */
@Data
@Builder
public class DeploymentResult {
    
    private String deploymentId;
    
    private String processDefinitionId;
    
    private String processDefinitionKey;
    
    private String name;
    
    private Integer version;
    
    private LocalDateTime deploymentTime;
    
    private boolean success;
    
    private String message;
    
    public static DeploymentResult success(String deploymentId, String processDefinitionId, 
                                         String processDefinitionKey, String name, Integer version) {
        return DeploymentResult.builder()
                .deploymentId(deploymentId)
                .processDefinitionId(processDefinitionId)
                .processDefinitionKey(processDefinitionKey)
                .name(name)
                .version(version)
                .deploymentTime(LocalDateTime.now())
                .success(true)
                .message("流程部署成功")
                .build();
    }
    
    public static DeploymentResult failure(String message) {
        return DeploymentResult.builder()
                .success(false)
                .message(message)
                .deploymentTime(LocalDateTime.now())
                .build();
    }
}