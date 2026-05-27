package com.workflow.dto.response;

import com.workflow.enums.VariableType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Variable get result DTO
 * 
 * Returns the query result of process variables
 * Includes variable value, type, and metadata
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariableGetResult {

    /**
     * Variable name
     */
    private String name;

    /**
     * Variable value
     */
    private Object value;

    /**
     * Variable type
     */
    private VariableType type;

    /**
     * Variable scope
     */
    private String scope;

    /**
     * Process instance ID
     */
    private String processInstanceId;

    /**
     * Execution ID
     */
    private String executionId;

    /**
     * Task ID
     */
    private String taskId;

    /**
     * Whether variable found
     */
    private Boolean found;

    /**
     * Variable creation time
     */
    private LocalDateTime createdTime;

    /**
     * Variable update time
     */
    private LocalDateTime updatedTime;

    /**
     * Created by
     */
    private String createdBy;

    /**
     * Updated by
     */
    private String updatedBy;

    /**
     * Tenant ID
     */
    private String tenantId;

    /**
     * Sequence counter (version number)
     */
    private Long sequenceCounter;

    /**
     * Whether concurrent local variable
     */
    private Boolean isConcurrentLocal;

    /**
     * Create a successful result
     * 
     * @param name Variable name
     * @param value Variable value
     * @param type Variable type
     * @return Successful result
     */
    public static VariableGetResult success(String name, Object value, VariableType type) {
        return VariableGetResult.builder()
                .name(name)
                .value(value)
                .type(type)
                .found(true)
                .build();
    }

    /**
     * Create a not-found result
     * 
     * @param name Variable name
     * @return Not-found result
     */
    public static VariableGetResult notFound(String name) {
        return VariableGetResult.builder()
                .name(name)
                .found(false)
                .build();
    }

    /**
     * Get formatted variable value string
     * 
     * @return Formatted value
     */
    public String getFormattedValue() {
        if (value == null) {
            return "null";
        }
        
        if (type == null) {
            return value.toString();
        }
        
        switch (type) {
            case STRING:
                return "\"" + value.toString() + "\"";
            case JSON:
                return value.toString();
            case DATE:
                return value.toString();
            case BOOLEAN:
                return value.toString();
            case INTEGER:
            case LONG:
            case DOUBLE:
                return value.toString();
            default:
                return value.toString();
        }
    }

    /**
     * Check if variable is null/empty
     * 
     * @return true if null/empty
     */
    public boolean isEmpty() {
        return !found || value == null;
    }

    /**
     * Get variable size (estimated byte count)
     * 
     * @return Variable size
     */
    public long getEstimatedSize() {
        if (value == null) {
            return 0;
        }
        
        if (type == null) {
            return value.toString().length() * 2; // Estimated Unicode character size
        }
        
        switch (type) {
            case STRING:
                return value.toString().length() * 2;
            case INTEGER:
                return 4;
            case LONG:
                return 8;
            case DOUBLE:
                return 8;
            case BOOLEAN:
                return 1;
            case DATE:
                return 8;
            case JSON:
                return value.toString().length() * 2;
            case BINARY:
                if (value instanceof byte[]) {
                    return ((byte[]) value).length;
                }
                return value.toString().length() * 2;
            default:
                return value.toString().length() * 2;
        }
    }
}
