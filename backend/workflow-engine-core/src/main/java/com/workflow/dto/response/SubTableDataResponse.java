package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Sub-table data query response.
 * Used for real-time sync of sub-table data in main task forms.
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
     * Task ID
     */
    private String taskId;
    
    /**
     * Sub-table name
     */
    private String subTableName;
    
    /**
     * Sub-table data row list
     */
    private List<SubTableRow> rows;
    
    /**
     * Sub-table data row
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubTableRow {
        /**
         * Row ID
         */
        private Long id;

        /**
         * Physical table primary key columns -> values (non-null for composite keys; usually same as {@link #id} for single numeric PK)
         */
        private Map<String, Object> rowKey;
        
        /**
         * Row data (contains all fields)
         */
        private Map<String, Object> data;
        
        /**
         * Assignee ID
         */
        private String assignee;
        
        /**
         * Assignee name
         */
        private String assigneeName;
        
        /**
         * Task status (ASSIGNED, COMPLETED, CANCELLED, etc.)
         */
        private String status;
    }
}
