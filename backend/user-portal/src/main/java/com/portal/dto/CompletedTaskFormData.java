package com.portal.dto;

import lombok.Data;
import lombok.Builder;

import java.util.Map;

/**
 * 已完成任务表单数据响应 DTO
 * GET /api/portal/tasks/{taskId}/completed-form
 */
@Data
@Builder
public class CompletedTaskFormData {
    private TaskFormSnapshot snapshot;
    private Map<String, Object> liveValues;
    private boolean showLiveValues;
    private ProcessFormData processFormRef;
}
