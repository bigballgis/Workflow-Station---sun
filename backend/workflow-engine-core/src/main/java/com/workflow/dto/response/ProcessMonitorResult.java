package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
public class ProcessMonitorResult {
    
    /**
     * 操作是否成功
     */
    private boolean success;
    
    /**
     * 流程实例列表
     */
    private List<Map<String, Object>> processInstances;
    
    /**
     * 总记录数
     */
    private Long totalCount;
    
    /**
     * 当前页码
     */
    private Integer currentPage;
    
    /**
     * 页面大小
     */
    private Integer pageSize;
    
    /**
     * 错误信息
     */
    private String errorMessage;
}