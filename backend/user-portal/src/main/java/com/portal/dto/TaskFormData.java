package com.portal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.portal.util.SubTableBatchMetadata;

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
    @JsonIgnoreProperties({SubTableBatchMetadata.RUNTIME_FIELD, SubTableBatchMetadata.DELETED_ROWS_FIELD})
    private Map<String, Object> fieldValues;
    private List<SubTableBindingData> subTableBindings;
    private ProcessFormData processFormRef;
    private Boolean formReadOnly;
    /** Server-filtered batch controls from this instance's frozen runtime snapshot. */
    private Map<String, Object> subTableBatchCapabilities;
    /** Whether this instance uses the enhanced server-owned write contract. */
    private Boolean subTableBatchRuntimeEnabled;
    /** Main-table Request ID config ({fieldNames, separator}) so the portal can recompute it live. */
    private Map<String, Object> requestIdConfig;
}
