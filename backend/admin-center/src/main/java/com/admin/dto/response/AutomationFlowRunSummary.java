package com.admin.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 自动化 flow 一次执行的概要（运行记录列表用）。
 *
 * <p>数据源是 AP 的 {@code flow_run} 表（读走共库 SQL，与
 * {@link AutomationFlowSummary} 同模式）。逐步骤输出不在此处——它存在 AP 的
 * logs 文件里，详情按需经 AP API 取（见 {@code AutomationFlowRunService#getRunDetail}）。</p>
 */
@Data
@Builder
public class AutomationFlowRunSummary {

    private String id;

    private String flowId;

    /** 迁移键（flow.metadata.hermesFlowKey）；本环境原生 flow 无此值时为 null */
    private String flowKey;

    /** 执行时所用版本的显示名（版本改名后历史记录仍显示当时的名字） */
    private String flowDisplayName;

    private String projectId;

    private String projectName;

    /** AP 运行状态：SUCCEEDED / FAILED / RUNNING / PAUSED / ... */
    private String status;

    private OffsetDateTime startTime;

    private OffsetDateTime finishTime;

    /** 执行耗时（毫秒）；未结束的运行为 null */
    private Long durationMs;

    /** 失败步骤显示名（flow_run.failedStep）；成功的运行为 null */
    private String failedStepName;

    /** 失败步骤的错误信息（AP 侧已截断到 700 字符） */
    private String failedStepMessage;
}
