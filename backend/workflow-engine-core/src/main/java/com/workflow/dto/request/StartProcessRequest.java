package com.workflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 启动流程实例请求
 */
@Data
public class StartProcessRequest {
    
    @NotBlank(message = "流程定义键不能为空")
    private String processDefinitionKey;
    
    private String businessKey;
    
    private String processInstanceName;
    
    private Map<String, Object> variables;
    
    private String startUserId;
    
    private String tenantId;
}