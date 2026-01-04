package com.workflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 任务完成请求
 */
@Data
public class TaskCompleteRequest {
    
    @NotBlank(message = "用户ID不能为空")
    private String userId;
    
    private Map<String, Object> formData;
    
    private String completionComment;
    
    private Map<String, Object> localVariables;
}