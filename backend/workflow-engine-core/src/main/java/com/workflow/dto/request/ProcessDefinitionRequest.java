package com.workflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 流程定义部署请求
 */
@Data
public class ProcessDefinitionRequest {
    
    @NotBlank(message = "Process definition name is required")
    private String name;
    
    @NotBlank(message = "Process definition key is required")
    private String key;
    
    private String category;
    
    private String description;
    
    @NotNull(message = "BPMN XML content is required")
    private String bpmnXml;
    
    private String tenantId;
    
    private boolean activate = true;
}