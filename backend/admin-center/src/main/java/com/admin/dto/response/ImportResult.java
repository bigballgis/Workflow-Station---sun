package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 功能包导入结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportResult {
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 功能单元信息
     */
    private FunctionUnitInfo functionUnit;
    
    /**
     * 验证错误列表
     */
    @Builder.Default
    private List<ValidationError> validationErrors = new ArrayList<>();
    
    /**
     * 依赖冲突列表
     */
    @Builder.Default
    private List<DependencyConflict> conflicts = new ArrayList<>();
    
    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 是否为「同名加版本」导入（名称已存在，作为新版本导入）。
     */
    private boolean versioned;

    /**
     * 创建成功结果
     */
    public static ImportResult success(FunctionUnitInfo functionUnit) {
        return ImportResult.builder()
                .success(true)
                .functionUnit(functionUnit)
                .build();
    }

    /**
     * 创建成功结果，并标记是否为同名加版本。
     */
    public static ImportResult success(FunctionUnitInfo functionUnit, boolean versioned) {
        return ImportResult.builder()
                .success(true)
                .functionUnit(functionUnit)
                .versioned(versioned)
                .build();
    }
    
    /**
     * 创建失败结果
     */
    public static ImportResult failure(String errorMessage) {
        return ImportResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
    
    /**
     * 创建验证失败结果
     */
    public static ImportResult validationFailed(List<ValidationError> errors) {
        return ImportResult.builder()
                .success(false)
                .validationErrors(errors)
                .errorMessage("功能包验证失败")
                .build();
    }
    
    /**
     * 创建冲突结果
     */
    public static ImportResult conflictDetected(FunctionUnitInfo functionUnit, List<DependencyConflict> conflicts) {
        return ImportResult.builder()
                .success(true)
                .functionUnit(functionUnit)
                .conflicts(conflicts)
                .build();
    }
    
    /**
     * 验证错误
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationError {
        private String type;
        private String field;
        private String message;
        private String details;
    }
    
    /**
     * 依赖冲突
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DependencyConflict {
        private String dependencyCode;
        private String requiredVersion;
        private String existingVersion;
        private String conflictType;
    }
}
