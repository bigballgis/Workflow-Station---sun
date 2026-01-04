package com.workflow.dto.response;

import com.workflow.enums.VariableType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 变量获取结果DTO
 * 
 * 返回流程变量的查询结果
 * 包含变量值、类型和元数据信息
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
     * 变量名称
     */
    private String name;

    /**
     * 变量值
     */
    private Object value;

    /**
     * 变量类型
     */
    private VariableType type;

    /**
     * 变量作用域
     */
    private String scope;

    /**
     * 流程实例ID
     */
    private String processInstanceId;

    /**
     * 执行ID
     */
    private String executionId;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 是否找到变量
     */
    private Boolean found;

    /**
     * 变量创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 变量更新时间
     */
    private LocalDateTime updatedTime;

    /**
     * 创建人
     */
    private String createdBy;

    /**
     * 更新人
     */
    private String updatedBy;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 序列计数器（版本号）
     */
    private Long sequenceCounter;

    /**
     * 是否为并发本地变量
     */
    private Boolean isConcurrentLocal;

    /**
     * 创建成功的结果
     * 
     * @param name 变量名称
     * @param value 变量值
     * @param type 变量类型
     * @return 成功结果
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
     * 创建未找到的结果
     * 
     * @param name 变量名称
     * @return 未找到结果
     */
    public static VariableGetResult notFound(String name) {
        return VariableGetResult.builder()
                .name(name)
                .found(false)
                .build();
    }

    /**
     * 获取格式化的变量值字符串
     * 
     * @return 格式化的值
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
     * 判断变量是否为空值
     * 
     * @return true如果为空值
     */
    public boolean isEmpty() {
        return !found || value == null;
    }

    /**
     * 获取变量大小（字节数估算）
     * 
     * @return 变量大小
     */
    public long getEstimatedSize() {
        if (value == null) {
            return 0;
        }
        
        if (type == null) {
            return value.toString().length() * 2; // 估算Unicode字符大小
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