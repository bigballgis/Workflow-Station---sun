package com.workflow.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 并行网关处理结果
 * 用于表示并行网关的分支创建和合并状态
 */
@Data
@Builder
public class ParallelGatewayResult {
    
    /**
     * 网关ID
     */
    private String gatewayId;
    
    /**
     * 网关名称
     */
    private String gatewayName;
    
    /**
     * 网关类型
     */
    private String gatewayType;
    
    /**
     * 是否为分支网关
     */
    private boolean isForkGateway;
    
    /**
     * 是否为合并网关
     */
    private boolean isJoinGateway;
    
    /**
     * 网关状态
     */
    private String status;
    
    /**
     * 分支信息列表
     */
    private List<BranchInfo> branches;
    
    /**
     * 总分支数
     */
    private int totalBranches;
    
    /**
     * 活动分支数
     */
    private int activeBranches;
    
    /**
     * 已完成分支数
     */
    private int completedBranches;
    
    /**
     * 评估时间
     */
    private LocalDateTime evaluationTime;
    
    /**
     * 分支信息
     */
    @Data
    @Builder
    public static class BranchInfo {
        
        /**
         * 分支ID
         */
        private String branchId;
        
        /**
         * 分支名称
         */
        private String branchName;
        
        /**
         * 目标活动ID（分支网关）
         */
        private String targetActivityId;
        
        /**
         * 源活动ID（合并网关）
         */
        private String sourceActivityId;
        
        /**
         * 分支状态
         */
        private String status;
        
        /**
         * 创建时间
         */
        private LocalDateTime createdTime;
        
        /**
         * 完成时间
         */
        private LocalDateTime completedTime;
    }
}