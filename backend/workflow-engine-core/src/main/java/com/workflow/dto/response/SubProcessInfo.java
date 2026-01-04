package com.workflow.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 子流程信息
 * 用于表示子流程和调用活动的执行状态
 */
@Data
@Builder
public class SubProcessInfo {
    
    /**
     * 子流程实例ID
     */
    private String subProcessInstanceId;
    
    /**
     * 子流程定义键
     */
    private String subProcessDefinitionKey;
    
    /**
     * 子流程定义名称
     */
    private String subProcessDefinitionName;
    
    /**
     * 调用活动ID（如果是调用活动）
     */
    private String callActivityId;
    
    /**
     * 业务键
     */
    private String businessKey;
    
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 启动用户ID
     */
    private String startUserId;
    
    /**
     * 是否为活动状态
     */
    private boolean isActive;
    
    /**
     * 是否为暂停状态
     */
    private boolean isSuspended;
    
    /**
     * 是否为嵌入式子流程
     */
    @Builder.Default
    private boolean isEmbedded = false;
}