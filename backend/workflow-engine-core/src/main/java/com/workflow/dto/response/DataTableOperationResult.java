package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 数据表操作结果DTO
 * 
 * 返回数据表增删改操作的结果
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataTableOperationResult {
    
    /**
     * 操作是否成功
     */
    private boolean success;
    
    /**
     * 错误消息（如果操作失败）
     */
    private String errorMessage;
    
    /**
     * 受影响的行数
     */
    private int affectedRows;
    
    /**
     * 生成的主键（用于插入操作）
     */
    private Map<String, Object> generatedKeys;
    
    /**
     * 执行的SQL语句（调试用）
     */
    private String executedSql;
}