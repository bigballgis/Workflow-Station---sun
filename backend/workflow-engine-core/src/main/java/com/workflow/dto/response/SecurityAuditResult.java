package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 安全审计结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAuditResult {
    
    /**
     * 报告生成时间
     */
    private LocalDateTime reportTime;
    
    /**
     * 统计开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 统计结束时间
     */
    private LocalDateTime endTime;
    
    /**
     * 安全评分（0-100）
     */
    private int securityScore;
    
    /**
     * 成功登录次数
     */
    private long successfulLogins;
    
    /**
     * 失败登录次数
     */
    private long failedLogins;
    
    /**
     * 角色分配次数
     */
    private long roleAssignments;
    
    /**
     * 角色撤销次数
     */
    private long roleRevocations;
    
    /**
     * 可疑活动列表
     */
    private List<SuspiciousActivity> suspiciousActivities;
    
    /**
     * 活跃用户数
     */
    private long activeUsers;
    
    /**
     * 锁定账户数
     */
    private long lockedAccounts;
    
    /**
     * 可疑活动DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuspiciousActivity {
        
        /**
         * 活动类型
         */
        private String type;
        
        /**
         * 描述
         */
        private String description;
        
        /**
         * 严重程度（HIGH, MEDIUM, LOW）
         */
        private String severity;
        
        /**
         * 相关用户名
         */
        private String username;
        
        /**
         * 相关IP地址
         */
        private String ipAddress;
        
        /**
         * 检测时间
         */
        private LocalDateTime detectedTime;
        
        /**
         * 是否已处理
         */
        private Boolean handled;
    }
}
