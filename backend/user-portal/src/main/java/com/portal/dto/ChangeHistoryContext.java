package com.portal.dto;

import lombok.Data;
import lombok.Builder;

/**
 * 变更历史上下文 DTO
 */
@Data
@Builder
public class ChangeHistoryContext {
    private String processInstanceId;
    private String taskInstanceId;  // nullable for Process Form changes
    private String stageId;
    private String userId;
}
