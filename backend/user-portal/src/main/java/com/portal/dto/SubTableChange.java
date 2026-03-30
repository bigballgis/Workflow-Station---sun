package com.portal.dto;

import lombok.Data;
import lombok.Builder;

import java.util.Map;

/**
 * 子表变更记录 DTO
 */
@Data
@Builder
public class SubTableChange {
    private String changeType;      // ROW_ADD, ROW_UPDATE, ROW_DELETE
    private String rowIdentifier;
    private Map<String, Object> oldValues;  // null for ROW_ADD
    private Map<String, Object> newValues;  // null for ROW_DELETE
}
