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
    /**
     * 「当前步骤」名（MI 感知）：普通节点=currentNode；当流程正处于多实例子任务内部时=外层多实例
     * subProcess 的 name（如 "multi"），供详情 Basic Info 的 Current Step 展示「进到多实例这一大步」。
     * 终态（COMPLETED 等）由前端显示 '-'。
     */
    private String currentStepName;
    private String currentAssignee;
    private String candidateUsers;
    private Map<String, Object> variables;

    /**
     * Request ID:主表配置的有序字段 + 分隔符拼成的人类可读标识(如 HR-2026-001),
     * 由 RequestIdEnricher 填充;主表未配置时为 null(前端列表渲染 '-')。
     */
    private String requestId;

    /**
     * 首个发起人任务是否自动完成失败：成功（或本就不该自动完成）为 null，失败为固定标记
     * {@code FIRST_STEP_NOT_COMPLETED}。
     *
     * <p>实例此时确实已创建并 RUNNING，任务退回发起人待办可重试，所以 {@code /start} 不报错——
     * 但也不能让前端弹「提交成功」。非 null 即表示「申请已建、首步未完成」，前端据此改提示。
     * 典型来源：服务任务里的 AP 自动化失败（见 workflow-engine 的 ApFlowNoResponseException）。
     *
     * <p><b>刻意不回传具体原因</b>：引擎原文里带 AP sync webhook URL，而 AP CE 的 webhook
     * 无鉴权、URL 即凭据，且经 Kong 的 {@code /api/ap/*} 浏览器可直达。真实原因只写服务端日志。
     */
    private String firstStepError;

    /** 发起时钉死的功能单元目录 ID（admin sys_function_units.id） */
    private String functionUnitCatalogId;
    private String functionUnitCode;
    private String functionUnitVersionLabel;
}
