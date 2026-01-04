package com.workflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 流程定义部署请求
 */
@Data
public class ProcessDefinitionRequest {
    
    @NotBlank(message = "流程定义名称不能为空")
    private String name;
    
    @NotBlank(message = "流程定义键不能为空")
    private String key;
    
    private String category;
    
    private String description;
    
    @NotNull(message = "BPMN XML内容不能为空")
    private String bpmnXml;
    
    private String tenantId;
    
    private boolean activate = true;
}