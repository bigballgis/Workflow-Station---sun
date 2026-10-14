package com.portal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.portal.util.SubTableBatchMetadata;

import lombok.Data;
import lombok.Builder;

import java.time.Instant;
import java.util.Map;

/**
 * 已完成任务的表单数据快照 DTO
 * 快照存储在 Flowable 流程变量中，key 格式: _snapshot_{taskId}
 */
@Data
@Builder
public class TaskFormSnapshot {
    private String taskId;
    private String taskDefinitionKey;
    private String assignee;
    private Instant completedAt;
    @JsonIgnoreProperties(SubTableBatchMetadata.RUNTIME_FIELD)
    private Map<String, Object> fieldValues;
}
