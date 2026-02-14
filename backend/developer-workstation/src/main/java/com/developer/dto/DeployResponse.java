package com.developer.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 部署响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeployResponse {
    
    /**
     * 部署ID
     */
    private String deploymentId;
    
    /**
     * 部署状态
     */
    private DeployStatus status;
    
    /**
     * 消息
     */
    private String message;
    
    /**
     * 进度百分比
     */
    private Integer progress;
    
    /**
     * 部署步骤
     */
    private List<DeployStep> steps;
    
    /**
     * 部署时间
     */
    private LocalDateTime deployedAt;
    
    /**
     * 自动创建的版本号
     */
    private String versionNumber;
    
    /**
     * 变更日志
     */
    private String changeLog;
    
    public enum DeployStatus {
        PENDING,
        DEPLOYING,
        SUCCESS,
        FAILED,
        ROLLED_BACK
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeployStep {
        private String name;
        private String status;
        private String message;
        private LocalDateTime completedAt;
    }
}
