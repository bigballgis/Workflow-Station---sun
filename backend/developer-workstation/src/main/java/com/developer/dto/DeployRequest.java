package com.developer.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 一键部署请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeployRequest {
    
    /**
     * 目标管理员中心URL
     */
    @Size(max = 500)
    private String targetUrl;
    
    /**
     * 部署环境
     */
    private DeployEnvironment environment;
    
    /**
     * 冲突处理策略
     */
    @Size(max = 50)
    private String conflictStrategy;
    
    /**
     * 是否自动启用
     */
    private Boolean autoEnable;
    
    /**
     * 版本变更日志（可选）
     */
    @Size(max = 2000)
    private String changeLog;
    
    public enum DeployEnvironment {
        DEVELOPMENT,
        TESTING,
        PRODUCTION
    }
}
