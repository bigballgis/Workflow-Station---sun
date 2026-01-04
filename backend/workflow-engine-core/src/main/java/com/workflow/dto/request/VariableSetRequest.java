package com.workflow.dto.request;

import com.workflow.enums.VariableScope;
import com.workflow.enums.VariableType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 变量设置请求DTO
 * 
 * 用于设置流程变量的请求参数
 * 支持多种变量类型和作用域
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariableSetRequest {

    /**
     * 变量名称
     */
    @NotBlank(message = "变量名称不能为空")
    private String name;

    /**
     * 变量值
     */
    private Object value;

    /**
     * 变量类型
     */
    @NotNull(message = "变量类型不能为空")
    private VariableType type;

    /**
     * 变量作用域
     */
    @NotNull(message = "变量作用域不能为空")
    private VariableScope scope;

    /**
     * 流程实例ID（流程实例级别变量必需）
     */
    private String processInstanceId;

    /**
     * 执行ID（执行级别变量必需）
     */
    private String executionId;

    /**
     * 任务ID（任务级别变量必需）
     */
    private String taskId;

    /**
     * 变更原因
     */
    private String changeReason;

    /**
     * 操作人
     */
    private String operatedBy;

    /**
     * 是否为本地变量
     */
    @Builder.Default
    private Boolean isLocal = false;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 验证请求参数的有效性
     * 
     * @return 验证结果
     */
    public boolean isValid() {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        if (type == null || scope == null) {
            return false;
        }
        
        // 根据作用域验证必需的ID
        switch (scope) {
            case PROCESS_INSTANCE:
                return processInstanceId != null && !processInstanceId.trim().isEmpty();
            case EXECUTION:
                return executionId != null && !executionId.trim().isEmpty();
            case TASK:
                return taskId != null && !taskId.trim().isEmpty();
            case GLOBAL:
                return true; // 全局变量不需要特定ID
            default:
                return false;
        }
    }

    /**
     * 获取变量的完整标识符
     * 
     * @return 变量标识符
     */
    public String getVariableIdentifier() {
        StringBuilder identifier = new StringBuilder();
        identifier.append(scope.getCode()).append(":");
        
        switch (scope) {
            case PROCESS_INSTANCE:
                identifier.append(processInstanceId);
                break;
            case EXECUTION:
                identifier.append(executionId);
                break;
            case TASK:
                identifier.append(taskId);
                break;
            case GLOBAL:
                identifier.append("global");
                break;
        }
        
        identifier.append(":").append(name);
        return identifier.toString();
    }
}