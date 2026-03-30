package com.portal.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * Task Form 提交请求 DTO
 * POST /api/portal/tasks/{taskId}/submit
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskFormSubmitRequest {
    @NotNull
    private Map<String, Object> formData;
    private Map<String, List<Map<String, Object>>> subTableData;
    /**
     * 基准值：前端加载表单时的字段值快照。
     * 用于并发修改检测 — 提交时对比基准值与当前流程变量，
     * 若某字段的当前值 != 基准值，说明已被其他用户修改（并发）。
     */
    private Map<String, Object> baselineValues;
}
