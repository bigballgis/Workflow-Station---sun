package com.portal.dto;

import lombok.Data;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * Process Form 数据响应 DTO
 * GET /api/portal/processes/{processInstanceId}/form
 */
@Data
@Builder
public class ProcessFormData {
    private String processInstanceId;
    private String formName;
    private String formType;
    private Map<String, Object> configJson;
    private Map<String, Object> fieldValues;
    private List<SubTableBindingData> subTableBindings;
    private boolean editable;
    private String processState;
    /** Main-table Request ID config ({fieldNames, separator}) so the portal can recompute it live. */
    private Map<String, Object> requestIdConfig;
}
