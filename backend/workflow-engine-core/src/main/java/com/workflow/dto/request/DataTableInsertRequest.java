package com.workflow.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 数据表插入请求DTO
 * 
 * 用于向数据表插入新记录
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataTableInsertRequest {
    
    /**
     * 表名
     */
    private String tableName;
    
    /**
     * 插入的数据，key为字段名，value为字段值
     */
    private Map<String, Object> data;
    
    /**
     * 是否返回插入的记录ID
     */
    private boolean returnGeneratedKeys;
}