package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 子表数据查询响应
 * 用于主任务表单实时同步子表数据
 * 
 * **Validates: Requirements 7.1**
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubTableDataResponse {
    
    /**
     * 任务ID
     */
    private String taskId;
    
    /**
     * 子表名称
     */
    private String subTableName;
    
    /**
     * 子表数据行列表
     */
    private List<SubTableRow> rows;
    
    /**
     * 子表数据行
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubTableRow {
        /**
         * 行ID
         */
        private Long id;
        
        /**
         * 行数据（包含所有字段）
         */
        private Map<String, Object> data;
        
        /**
         * 处理人ID
         */
        private String assignee;
        
        /**
         * 处理人姓名
         */
        private String assigneeName;
        
        /**
         * 任务状态（ASSIGNED, COMPLETED, CANCELLED等）
         */
        private String status;
    }
}
