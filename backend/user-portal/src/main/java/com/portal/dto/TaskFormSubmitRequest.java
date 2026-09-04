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
    /**
     * 本次提交里被**主动删空**的参与者子表切片（canonical store key，如 {@code dw:people}）。
     *
     * <p>空数组切片本身是歧义的：既可能是「用户删掉了自己最后一行」，也可能是「这个 binding 根本
     * 没渲染 / MI 隔离重建 payload 时没带上」。后端不猜，由前端在这里显式声明；只有列出的 key
     * 才允许清掉该参与者的基线行（见 {@code MiSubTaskSubTableRowMerger} 的 empty-slice 分支）。
     *
     * <p><b>刻意不放在 {@code formData} 里</b>：approve/complete 链路会把 {@code formData} 整体
     * 灌进流程变量，放进去就会被当成业务变量持久化。这是传输元数据，不是表单字段。
     */
    private List<String> emptiedSubTableKeys;
}
