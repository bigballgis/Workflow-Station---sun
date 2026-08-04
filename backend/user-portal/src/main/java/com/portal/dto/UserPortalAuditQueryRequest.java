package com.portal.dto;

import lombok.Data;

/**
 * 全局审计日志查询请求参数
 */
@Data
public class UserPortalAuditQueryRequest {
    /** 按用户 ID 过滤 */
    private String userId;
    /** 按用户名称模糊匹配 */
    private String username;
    /** 按功能单元 code 过滤 */
    private String functionUnitCode;
    /** 按操作类型过滤（FIELD_UPDATE, SUB_TABLE_ROW_ADD 等） */
    private String changeType;
    /** 查询起始时间（ISO-8601） */
    private String startTime;
    /** 查询截止时间（ISO-8601） */
    private String endTime;
    /**
     * Keyword for process instance filter: matches instance id, title, business key,
     * process definition name, or computed Request ID (case-insensitive substring).
     */
    private String processInstanceId;
    /** 分页页码（0-based） */
    private int page;
    /** 每页大小 */
    private int size;
    /** 排序字段 */
    private String sortField;
    /** 排序方向: asc / desc */
    private String sortOrder;
}
