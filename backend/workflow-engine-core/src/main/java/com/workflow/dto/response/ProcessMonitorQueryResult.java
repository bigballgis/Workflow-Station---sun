package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 流程监控查询结果
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessMonitorQueryResult {

    /**
     * 操作是否成功
     */
    private boolean success;

    /**
     * 流程监控数据列表
     */
    private List<Map<String, Object>> processMonitorData;

    /**
     * 总记录数
     */
    private long totalCount;

    /**
     * 当前页码
     */
    private int currentPage;

    /**
     * 页大小
     */
    private int pageSize;

    /**
     * 统计信息
     */
    private Map<String, Object> statistics;

    /**
     * 性能指标
     */
    private Map<String, Object> performanceMetrics;

    /**
     * 查询时间
     */
    private LocalDateTime queryTime;

    /**
     * 错误消息
     */
    private String errorMessage;
}