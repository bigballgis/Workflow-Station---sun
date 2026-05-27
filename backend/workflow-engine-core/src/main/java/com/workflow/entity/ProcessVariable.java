package com.workflow.entity;

import com.workflow.enums.VariableType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * Process variable entity
 * 
 * Used to store the history and extended information of process variables
 * Supports multiple data types and PostgreSQL JSONB storage
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Entity
@Table(name = "wf_process_variables", indexes = {
    @Index(name = "idx_variable_name", columnList = "name"),
    @Index(name = "idx_variable_proc_inst", columnList = "processInstanceId"),
    @Index(name = "idx_variable_task", columnList = "taskId"),
    @Index(name = "idx_variable_created_time", columnList = "createdTime")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessVariable {

    /**
     * Primary key ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 64)
    private String id;

    /**
     * Variable name
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * Variable type
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private VariableType type;

    /**
     * Process instance ID
     */
    @Column(name = "process_instance_id", length = 64)
    private String processInstanceId;

    /**
     * Execution ID
     */
    @Column(name = "execution_id", length = 64)
    private String executionId;

    /**
     * Task ID
     */
    @Column(name = "task_id", length = 64)
    private String taskId;

    /**
     * Case instance ID (CMMN support)
     */
    @Column(name = "case_instance_id", length = 64)
    private String caseInstanceId;

    /**
     * Case execution ID (CMMN support)
     */
    @Column(name = "case_execution_id", length = 64)
    private String caseExecutionId;

    /**
     * Activity instance ID
     */
    @Column(name = "activity_instance_id", length = 64)
    private String activityInstanceId;

    /**
     * Tenant ID (multi-tenant support)
     */
    @Column(name = "tenant_id", length = 255)
    private String tenantId;

    /**
     * Sequence counter (version control)
     */
    @Column(name = "sequence_counter")
    private Long sequenceCounter;

    /**
     * Whether concurrent local variable
     */
    @Column(name = "is_concurrent_local")
    @Builder.Default
    private Boolean isConcurrentLocal = false;

    // Different type value fields

    /**
     * Text value (String, Boolean, etc.)
     */
    @Column(name = "text_value", columnDefinition = "TEXT")
    private String textValue;

    /**
     * Extended text value
     */
    @Column(name = "text_value2", columnDefinition = "TEXT")
    private String textValue2;

    /**
     * Double value
     */
    @Column(name = "double_value")
    private Double doubleValue;

    /**
     * Long value
     */
    @Column(name = "long_value")
    private Long longValue;

    /**
     * Date value
     */
    @Column(name = "date_value")
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateValue;

    /**
     * JSON value (PostgreSQL JSONB support)
     */
    @Column(name = "json_value", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String jsonValue;

    /**
     * Binary data (files, etc.)
     */
    @Column(name = "binary_value", columnDefinition = "bytea")
    private byte[] binaryValue;

    /**
     * Created time
     */
    @Column(name = "created_time", nullable = false)
    private LocalDateTime createdTime;

    /**
     * Updated time
     */
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;

    /**
     * Creator
     */
    @Column(name = "created_by", length = 64)
    private String createdBy;

    /**
     * Updater
     */
    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    /**
     * Change reason
     */
    @Column(name = "change_reason", length = 500)
    private String changeReason;

    /**
     * Operation type (CREATE, UPDATE, DELETE)
     */
    @Column(name = "operation_type", length = 20)
    private String operationType;

    /**
     * Get the actual variable value
     * Return the corresponding field value based on type
     * 
     * @return Variable value
     */
    public Object getValue() {
        if (type == null) {
            return null;
        }
        
        switch (type) {
            case STRING:
                return textValue;
            case INTEGER:
                return longValue != null ? longValue.intValue() : null;
            case LONG:
                return longValue;
            case DOUBLE:
                return doubleValue;
            case BOOLEAN:
                return textValue != null ? Boolean.valueOf(textValue) : null;
            case DATE:
                return dateValue;
            case JSON:
                return jsonValue;
            case BINARY:
                return binaryValue;
            default:
                return textValue;
        }
    }

    /**
     * Set variable value
     * Set to the corresponding field based on type
     * 
     * @param value Variable value
     */
    public void setValue(Object value) {
        if (value == null) {
            return;
        }
        
        if (type == null) {
            // Auto-infer type
            type = inferType(value);
        }
        
        switch (type) {
            case STRING:
                textValue = value.toString();
                break;
            case INTEGER:
            case LONG:
                if (value instanceof Number) {
                    longValue = ((Number) value).longValue();
                } else {
                    longValue = Long.valueOf(value.toString());
                }
                break;
            case DOUBLE:
                if (value instanceof Number) {
                    doubleValue = ((Number) value).doubleValue();
                } else {
                    doubleValue = Double.valueOf(value.toString());
                }
                break;
            case BOOLEAN:
                textValue = value.toString();
                break;
            case DATE:
                if (value instanceof Date) {
                    dateValue = (Date) value;
                }
                break;
            case JSON:
                jsonValue = value.toString();
                break;
            case BINARY:
                if (value instanceof byte[]) {
                    binaryValue = (byte[]) value;
                }
                break;
            default:
                textValue = value.toString();
                break;
        }
    }

    /**
     * Infer variable type
     */
    private VariableType inferType(Object value) {
        if (value instanceof String) {
            return VariableType.STRING;
        } else if (value instanceof Integer) {
            return VariableType.INTEGER;
        } else if (value instanceof Long) {
            return VariableType.LONG;
        } else if (value instanceof Double || value instanceof Float) {
            return VariableType.DOUBLE;
        } else if (value instanceof Boolean) {
            return VariableType.BOOLEAN;
        } else if (value instanceof Date) {
            return VariableType.DATE;
        } else if (value instanceof byte[]) {
            return VariableType.BINARY;
        } else {
            return VariableType.JSON;
        }
    }

    @PrePersist
    protected void onCreate() {
        if (createdTime == null) {
            createdTime = LocalDateTime.now();
        }
        if (updatedTime == null) {
            updatedTime = LocalDateTime.now();
        }
        if (sequenceCounter == null) {
            sequenceCounter = 1L;
        }
        if (isConcurrentLocal == null) {
            isConcurrentLocal = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedTime = LocalDateTime.now();
        if (sequenceCounter != null) {
            sequenceCounter++;
        }
    }
}
