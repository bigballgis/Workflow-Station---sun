package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 数据表查询结果DTO
 * 
 * 返回数据表查询的结果
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataTableQueryResult {
    
    /**
     * 查询是否成功
     */
    private boolean success;
    
    /**
     * 错误消息（如果查询失败）
     */
    private String errorMessage;
    
    /**
     * 查询结果数据列表
     */
    private List<Map<String, Object>> data;
    
    /**
     * 总记录数（用于分页）
     */
    private Long totalCount;
    
    /**
     * 当前页码
     */
    private Integer currentPage;
    
    /**
     * 每页大小
     */
    private Integer pageSize;
    
    /**
     * 执行的SQL语句（调试用）
     */
    private String executedSql;
}