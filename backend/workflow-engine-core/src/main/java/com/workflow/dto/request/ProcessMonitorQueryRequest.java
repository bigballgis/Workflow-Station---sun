package com.workflow.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 流程监控查询请求
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessMonitorQueryRequest {
    
    /**
     * 流程定义键
     */
    private String processDefinitionKey;
    
    /**
     * 业务键
     */
    private String businessKey;
    
    /**
     * 流程状态：ACTIVE（运行中）、COMPLETED（已完成）、TERMINATED（已终止）
     */
    private String status;
    
    /**
     * 开始时间（流程启动时间范围）
     */
    private Date startTime;
    
    /**
     * 结束时间（流程启动时间范围）
     */
    private Date endTime;
    
    /**
     * 启动用户ID
     */
    private String startUserId;
    
    /**
     * 排序字段：id、startTime、endTime
     */
    private String orderBy;
    
    /**
     * 排序方向：ASC、DESC
     */
    private String orderDirection;
    
    /**
     * 分页大小
     */
    private Integer limit;
    
    /**
     * 偏移量
     */
    private Integer offset;
}