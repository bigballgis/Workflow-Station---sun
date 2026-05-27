package com.workflow.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * Assign sub-table row handler request.
 * 
 * Used in multi-instance sub-process pre-tasks to manually assign handlers for sub-table rows.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignSubTableRowRequest {
    
    /**
     * Assignee user ID
     */
    @NotBlank(message = "Assignee user ID must not be empty")
    private String assigneeId;

    /**
     * Required for composite primary keys; optional for single-column PK (can use path rowId instead).
     */
    private java.util.Map<String, Object> rowKey;
}
