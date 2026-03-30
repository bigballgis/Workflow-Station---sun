package com.portal.dto;

import lombok.Data;
import lombok.Builder;

import java.time.Instant;

/**
 * 变更历史查询响应 DTO
 */
@Data
@Builder
public class ChangeHistoryRecord {
    private Long id;
    private String processInstanceId;
    private String taskInstanceId;
    private String stageId;
    private String userId;
    private String userName;
    private Instant timestamp;
    private String fieldName;
    private String fieldLabel;
    private String oldValue;
    private String newValue;
    private String changeType;
    private String subTableName;
    private String rowIdentifier;
    private boolean concurrent;
}
