package com.admin.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 自动化 flow 概要（管理面列表用）。
 *
 * <p>flowKey 是跨环境迁移键：源环境导出时写入目标环境 flow 的
 * {@code metadata.hermesFlowKey}，引擎部署期据此把 BPMN 里的源 flowId
 * 解析为本环境实际 flowId（Q7 部署期解析）。</p>
 */
@Data
@Builder
public class AutomationFlowSummary {

    private String id;

    /** 迁移键（metadata.hermesFlowKey）；本环境原生 flow 无此值时为 null */
    private String flowKey;

    private String displayName;

    private String projectId;

    private String projectName;

    /** AP flow 状态：ENABLED / DISABLED */
    private String status;

    /** 是否有已发布版本（webhook 只触发已发布版本） */
    private boolean published;

    /** 最新版本是否通过 AP 校验 */
    private boolean valid;

    private String ownerName;

    private OffsetDateTime updated;

    /**
     * Display/filter value: DRAFT when unpublished, otherwise ENABLED / DISABLED.
     * Same CASE as the catalog page's readiness ladder.
     */
    private String readiness;
}
