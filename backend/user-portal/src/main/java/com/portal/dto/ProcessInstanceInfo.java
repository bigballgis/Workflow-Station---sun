package com.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessInstanceInfo {
    private String id;
    private String processDefinitionId;
    private String processDefinitionKey;
    private String processDefinitionName;
    private String businessKey;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 流程完成时间（库列 completed_at，与 endTime 同步维护时语义一致） */
    private LocalDateTime completedAt;
    private String title;
    private String status;
    private String startUserId;
    private String startUserName;
    private String currentNode;
    private String currentAssignee;
    private String candidateUsers;
    private Map<String, Object> variables;

    /**
     * Request ID:主表配置的有序字段 + 分隔符拼成的人类可读标识(如 HR-2026-001),
     * 由 RequestIdEnricher 填充;主表未配置时为 null(前端列表渲染 '-')。
     */
    private String requestId;

    /** 发起时钉死的功能单元目录 ID（admin sys_function_units.id） */
    private String functionUnitCatalogId;
    private String functionUnitCode;
    private String functionUnitVersionLabel;
}
