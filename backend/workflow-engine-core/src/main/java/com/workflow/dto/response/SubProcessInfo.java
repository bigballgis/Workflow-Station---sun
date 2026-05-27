package com.workflow.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Sub-process info
 * Used to represent the execution status of sub-processes and call activities
 */
@Data
@Builder
public class SubProcessInfo {
    
    /**
     * Sub-process instance ID
     */
    private String subProcessInstanceId;
    
    /**
     * Sub-process definition key
     */
    private String subProcessDefinitionKey;
    
    /**
     * Sub-process definition name
     */
    private String subProcessDefinitionName;
    
    /**
     * Call activity ID (if it is a call activity)
     */
    private String callActivityId;
    
    /**
     * Business key
     */
    private String businessKey;
    
    /**
     * Start time
     */
    private LocalDateTime startTime;
    
    /**
     * Start user ID
     */
    private String startUserId;
    
    /**
     * Whether active
     */
    private boolean isActive;
    
    /**
     * Whether suspended
     */
    private boolean isSuspended;
    
    /**
     * Whether embedded sub-process
     */
    @Builder.Default
    private boolean isEmbedded = false;
}