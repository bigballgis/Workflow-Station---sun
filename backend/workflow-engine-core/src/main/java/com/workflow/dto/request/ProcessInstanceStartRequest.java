package com.workflow.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * 流程实例启动请求
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessInstanceStartRequest {

    /**
     * 流程定义键
     */
    @NotBlank(message = "流程定义键不能为空")
    @Size(max = 255, message = "流程定义键长度不能超过255个字符")
    private String processDefinitionKey;

    /**
     * 业务键
     */
    @Size(max = 255, message = "业务键长度不能超过255个字符")
    private String businessKey;

    /**
     * 启动用户ID
     */
    @Size(max = 255, message = "启动用户ID长度不能超过255个字符")
    private String startUserId;

    /**
     * 流程变量
     */
    private Map<String, Object> variables;
}