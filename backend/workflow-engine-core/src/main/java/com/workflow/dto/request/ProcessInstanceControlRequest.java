package com.workflow.dto.request;

import lombok.Data;

/**
 * 流程实例控制请求（暂停、恢复、终止）
 */
@Data
public class ProcessInstanceControlRequest {
    
    /**
     * 流程实例ID
     */
    private String processInstanceId;
    
    /**
     * 操作类型（suspend, activate, terminate）
     */
    private String action;
    
    /**
     * 操作原因
     */
    private String reason;
    
    /**
     * 操作用户ID
     */
    private String userId;
}