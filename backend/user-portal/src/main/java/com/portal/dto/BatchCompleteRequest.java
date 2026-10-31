package com.portal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * To Do 批量通过/驳回：同一动作作用于多条任务，不带表单数据。
 */
public record BatchCompleteRequest(
        @NotEmpty @Size(max = 200) List<@NotBlank String> taskIds,
        @NotBlank String actionId,
        String comment,
        @Size(max = 200) Map<@NotBlank String, UUID> operationIds) {
    public BatchCompleteRequest(List<String> taskIds,String actionId,String comment) {
        this(taskIds,actionId,comment,Map.of());
    }
    public BatchCompleteRequest {
        operationIds=operationIds==null?Map.of():Map.copyOf(operationIds);
        if (taskIds!=null && !taskIds.containsAll(operationIds.keySet())) {
            throw new IllegalArgumentException("Completion operation must belong to a selected task");
        }
    }
}
