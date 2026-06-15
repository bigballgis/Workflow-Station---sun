package com.portal.dto;

import lombok.Data;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * Task Form data response DTO
 * GET /api/portal/tasks/{taskId}/form-data
 */
@Data
@Builder
public class TaskFormData {
    private String taskId;
    private String taskDefinitionKey;
    private String formName;
    private Map<String, Object> configJson;
    private Map<String, String> fieldPermissions;
    private Map<String, Object> fieldValues;
    private List<SubTableBindingData> subTableBindings;
    private ProcessFormData processFormRef;
    private Boolean formReadOnly;
    /** Main-table Request ID config ({fieldNames, separator}) so the portal can recompute it live. */
    private Map<String, Object> requestIdConfig;
}
