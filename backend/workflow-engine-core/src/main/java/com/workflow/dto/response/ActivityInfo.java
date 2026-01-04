package com.workflow.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 活动节点信息
 * 用于表示流程中的当前活动节点状态
 */
@Data
@Builder
public class ActivityInfo {
    
    /**
     * 执行实例ID
     */
    private String executionId;
    
    /**
     * 活动节点ID
     */
    private String activityId;
    
    /**
     * 活动节点名称
     */
    private String activityName;
    
    /**
     * 活动节点类型
     */
    private String activityType;
    
    /**
     * 是否为活动状态
     */
    private boolean isActive;
    
    /**
     * 是否为等待状态
     */
    private boolean isWaitState;
}