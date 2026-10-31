package com.workflow.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 流程实例启动结果
 */
@Data
@Builder
public class ProcessInstanceResult {
    
    private String processInstanceId;
    
    private String processDefinitionId;
    
    private String processDefinitionKey;
    
    private String businessKey;
    
    private String name;
    
    private LocalDateTime startTime;
    
    private String startUserId;
    
    private Map<String, Object> variables;

    /**
     * Active user-task name at start time (same meaning as status API {@code nextTaskName}).
     * Filled from the engine's TaskService in the start request; callers without a JWT can persist
     * {@code current_node} without a follow-up {@code GET /tasks}.
     */
    private String nextTaskName;
    
    private boolean success;
    
    private String message;

    /** Copies {@code nextTaskName} from {@link com.workflow.component.ProcessInstanceStatusReader}. */
    public void applyRuntimeStatus(Map<String, Object> status) {
        if (status == null) {
            return;
        }
        Object raw = status.get("nextTaskName");
        if (raw instanceof String name && !name.isBlank()) {
            this.nextTaskName = name.trim();
        }
    }
    
    public static ProcessInstanceResult success(String processInstanceId, String processDefinitionId,
                                              String processDefinitionKey, String businessKey, String name,
                                              String startUserId, Map<String, Object> variables) {
        return ProcessInstanceResult.builder()
                .processInstanceId(processInstanceId)
                .processDefinitionId(processDefinitionId)
                .processDefinitionKey(processDefinitionKey)
                .businessKey(businessKey)
                .name(name)
                .startTime(LocalDateTime.now())
                .startUserId(startUserId)
                .variables(variables)
                .success(true)
                .message("流程实例启动成功")
                .build();
    }
    
    public static ProcessInstanceResult failure(String message) {
        return ProcessInstanceResult.builder()
                .success(false)
                .message(message)
                .startTime(LocalDateTime.now())
                .build();
    }
}