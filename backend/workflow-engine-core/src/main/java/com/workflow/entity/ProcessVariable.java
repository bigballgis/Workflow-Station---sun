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
 * 流程变量实体类
 * 
 * 用于存储流程变量的历史记录和扩展信息
 * 支持多种数据类型和PostgreSQL JSONB存储
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
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 64)
    private String id;

    /**
     * 变量名称
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * 变量类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private VariableType type;

    /**
     * 流程实例ID
     */
    @Column(name = "process_instance_id", length = 64)
    private String processInstanceId;

    /**
     * 执行ID
     */
    @Column(name = "execution_id", length = 64)
    private String executionId;

    /**
     * 任务ID
     */
    @Column(name = "task_id", length = 64)
    private String taskId;

    /**
     * 案例实例ID（CMMN支持）
     */
    @Column(name = "case_instance_id", length = 64)
    private String caseInstanceId;

    /**
     * 案例执行ID（CMMN支持）
     */
    @Column(name = "case_execution_id", length = 64)
    private String caseExecutionId;

    /**
     * 活动实例ID
     */
    @Column(name = "activity_instance_id", length = 64)
    private String activityInstanceId;

    /**
     * 租户ID（多租户支持）
     */
    @Column(name = "tenant_id", length = 255)
    private String tenantId;

    /**
     * 序列计数器（版本控制）
     */
    @Column(name = "sequence_counter")
    private Long sequenceCounter;

    /**
     * 是否为并发本地变量
     */
    @Column(name = "is_concurrent_local")
    @Builder.Default
    private Boolean isConcurrentLocal = false;

    // 不同类型的值字段

    /**
     * 文本值（字符串、布尔值等）
     */
    @Column(name = "text_value", columnDefinition = "TEXT")
    private String textValue;

    /**
     * 扩展文本值
     */
    @Column(name = "text_value2", columnDefinition = "TEXT")
    private String textValue2;

    /**
     * 双精度浮点数值
     */
    @Column(name = "double_value")
    private Double doubleValue;

    /**
     * 长整数值
     */
    @Column(name = "long_value")
    private Long longValue;

    /**
     * 日期值
     */
    @Column(name = "date_value")
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateValue;

    /**
     * JSON值（PostgreSQL JSONB支持）
     */
    @Column(name = "json_value", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String jsonValue;

    /**
     * 二进制数据（文件等）
     */
    @Lob
    @Column(name = "binary_value")
    private byte[] binaryValue;

    /**
     * 创建时间
     */
    @Column(name = "created_time", nullable = false)
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;

    /**
     * 创建人
     */
    @Column(name = "created_by", length = 64)
    private String createdBy;

    /**
     * 更新人
     */
    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    /**
     * 变更原因
     */
    @Column(name = "change_reason", length = 500)
    private String changeReason;

    /**
     * 操作类型（CREATE, UPDATE, DELETE）
     */
    @Column(name = "operation_type", length = 20)
    private String operationType;

    /**
     * 获取实际的变量值
     * 根据类型返回相应字段的值
     * 
     * @return 变量值
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
     * 设置变量值
     * 根据类型设置到相应的字段
     * 
     * @param value 变量值
     */
    public void setValue(Object value) {
        if (value == null) {
            return;
        }
        
        if (type == null) {
            // 自动推断类型
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
     * 推断变量类型
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