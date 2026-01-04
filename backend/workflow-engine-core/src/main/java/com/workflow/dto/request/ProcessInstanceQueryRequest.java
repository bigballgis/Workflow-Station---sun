package com.workflow.dto.request;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 流程实例查询请求
 */
@Data
public class ProcessInstanceQueryRequest {
    
    /**
     * 流程实例ID
     */
    private String processInstanceId;
    
    /**
     * 流程定义键
     */
    private String processDefinitionKey;
    
    /**
     * 业务键
     */
    private String businessKey;
    
    /**
     * 启动用户ID
     */
    private String startUserId;
    
    /**
     * 流程实例状态（active, suspended, completed）
     */
    private String state;
    
    /**
     * 开始时间范围 - 起始
     */
    private LocalDateTime startTimeFrom;
    
    /**
     * 开始时间范围 - 结束
     */
    private LocalDateTime startTimeTo;
    
    /**
     * 流程变量过滤条件
     */
    private Map<String, Object> variables;
    
    /**
     * 分页参数 - 页码（从0开始）
     */
    private Integer page = 0;
    
    /**
     * 分页参数 - 每页大小
     */
    private Integer size = 20;
    
    /**
     * 排序字段
     */
    private String sortBy = "startTime";
    
    /**
     * 排序方向（asc, desc）
     */
    private String sortDirection = "desc";
}