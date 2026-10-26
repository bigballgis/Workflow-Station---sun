package com.portal.dto;

import lombok.Data;
import lombok.Builder;

/**
 * 变更历史上下文 DTO
 */
@Data
@Builder
public class ChangeHistoryContext {
    /** One actual save/event; shared by all main/sub-table writers for that operation. */
    @Builder.Default
    private String batchId = java.util.UUID.randomUUID().toString();
    @Builder.Default
    private java.time.Instant batchTime = java.time.Instant.now();
    private String processInstanceId;
    private String taskInstanceId;  // nullable for Process Form changes
    private String stageId;
    private String userId;
}
