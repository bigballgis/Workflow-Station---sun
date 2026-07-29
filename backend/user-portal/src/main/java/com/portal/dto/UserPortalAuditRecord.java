package com.portal.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

/**
 * 跨流程全局审计查询响应 DTO，用于 User Portal Audit Logs 页面。
 * 相比 {@link ChangeHistoryRecord}，额外包含 functionUnitCode / formName / tableName 等汇总字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPortalAuditRecord {
    private Long id;
    private String processInstanceId;
    private String taskInstanceId;
    private String stageId;
    /** BPMN 用户任务名称 */
    private String stageName;
    private String userId;
    private String userName;
    private Instant timestamp;
    private String fieldName;
    /** 从表单设计器解析的字段显示名 */
    private String fieldLabel;
    private String oldValue;
    private String newValue;
    private String changeType;
    private String subTableName;
    private String rowIdentifier;
    /** 从 up_process_instance 解析的 FU code */
    private String functionUnitCode;
    /** FU 显示名称（通过 admin-center API 解析） */
    private String functionUnitName;
    /** 流程表单名称 */
    private String formName;
    /** 主表 / 子表名称 */
    private String tableName;
    /**
     * Human-readable process label for audit list.
     * Priority: Request ID → title → businessKey → definitionName · shortId.
     */
    private String processTitle;
    /** Sub-table display name from table metadata; UI prefers this over {@link #subTableName}. */
    private String subTableDisplayName;
}
