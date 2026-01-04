package com.workflow.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 历史查询请求DTO
 * 支持复杂条件的历史数据查询
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryQueryRequest {

    // 分页参数
    private Integer page = 1;
    private Integer pageSize = 20;

    // 流程实例查询条件
    private String processDefinitionKey;
    private String processInstanceId;
    private String businessKey;
    private String startUserId;
    private LocalDateTime startTimeFrom;
    private LocalDateTime startTimeTo;
    private LocalDateTime endTimeFrom;
    private LocalDateTime endTimeTo;
    private Boolean finishedOnly;
    private Boolean unfinishedOnly;

    // 任务查询条件
    private String taskAssignee;
    private String taskName;
    private LocalDateTime taskStartTimeFrom;
    private LocalDateTime taskStartTimeTo;
    private LocalDateTime taskEndTimeFrom;
    private LocalDateTime taskEndTimeTo;

    // 变量查询条件
    private String variableName;
    private String variableNameLike;
    private String variableValue;

    // 全文搜索
    private String searchKeyword;

    // 导出相关
    private String exportFormat; // CSV, EXCEL, PDF
    private Boolean includeVariables = false;
    private Boolean includeTasks = false;
    private Boolean includeActivities = false;

    // 排序条件
    private String sortBy = "startTime";
    private String sortOrder = "desc"; // asc, desc

    // 租户ID
    private String tenantId;

    /**
     * 获取分页偏移量
     */
    public int getOffset() {
        return (page - 1) * pageSize;
    }

    /**
     * 验证请求参数的有效性
     */
    public boolean isValid() {
        return page != null && page > 0 && 
               pageSize != null && pageSize > 0 && pageSize <= 1000;
    }

    /**
     * 是否包含时间范围查询
     */
    public boolean hasTimeRange() {
        return startTimeFrom != null || startTimeTo != null || 
               endTimeFrom != null || endTimeTo != null;
    }

    /**
     * 是否包含任务时间范围查询
     */
    public boolean hasTaskTimeRange() {
        return taskStartTimeFrom != null || taskStartTimeTo != null || 
               taskEndTimeFrom != null || taskEndTimeTo != null;
    }

    /**
     * 是否为导出请求
     */
    public boolean isExportRequest() {
        return exportFormat != null && !exportFormat.trim().isEmpty();
    }

    /**
     * 是否为全文搜索请求
     */
    public boolean isFullTextSearch() {
        return searchKeyword != null && !searchKeyword.trim().isEmpty();
    }
}