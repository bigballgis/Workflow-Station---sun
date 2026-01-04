package com.workflow.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 数据表查询请求DTO
 * 
 * 用于动态查询数据表记录
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataTableQueryRequest {
    
    /**
     * 表名
     */
    private String tableName;
    
    /**
     * 查询字段列表，为空时查询所有字段
     */
    private List<String> selectFields;
    
    /**
     * 查询条件，key为字段名，value为字段值
     */
    private Map<String, Object> whereConditions;
    
    /**
     * 排序字段
     */
    private String orderBy;
    
    /**
     * 排序方向：ASC 或 DESC
     */
    private String orderDirection;
    
    /**
     * 分页偏移量
     */
    private Integer offset;
    
    /**
     * 分页大小
     */
    private Integer limit;
    
    /**
     * 连接条件（用于多表查询）
     */
    private List<JoinCondition> joinConditions;
    
    /**
     * 连接条件内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JoinCondition {
        /**
         * 连接类型：INNER, LEFT, RIGHT, FULL
         */
        private String joinType;
        
        /**
         * 连接的表名
         */
        private String joinTable;
        
        /**
         * 连接条件
         */
        private String onCondition;
    }
}