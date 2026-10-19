package com.workflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 启动流程实例请求
 */
@Data
public class StartProcessRequest {
    
    @NotBlank(message = "Process definition key is required")
    private String processDefinitionKey;

    /** Optional optimistic deployment pin; never selects an older definition. */
    private String expectedProcessDefinitionId;
    
    private String businessKey;
    
    private String processInstanceName;
    
    private Map<String, Object> variables;
    
    private String startUserId;
    
    private String tenantId;
}
