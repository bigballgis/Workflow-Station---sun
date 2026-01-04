package com.workflow.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 数据表删除请求DTO
 * 
 * 用于删除数据表记录
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataTableDeleteRequest {
    
    /**
     * 表名
     */
    private String tableName;
    
    /**
     * 删除条件，key为字段名，value为字段值
     */
    private Map<String, Object> whereConditions;
}