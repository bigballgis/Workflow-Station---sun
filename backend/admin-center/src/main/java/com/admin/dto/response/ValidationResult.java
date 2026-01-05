package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 功能包验证结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    
    /**
     * 是否验证通过
     */
    private boolean valid;
    
    /**
     * 文件格式验证结果
     */
    private boolean fileFormatValid;
    
    /**
     * 完整性验证结果
     */
    private boolean integrityValid;
    
    /**
     * 数字签名验证结果
     */
    private boolean signatureValid;
    
    /**
     * BPMN语法验证结果
     */
    private boolean bpmnSyntaxValid;
    
    /**
     * 数据表结构验证结果
     */
    private boolean dataTableValid;
    
    /**
     * 表单配置验证结果
     */
    private boolean formConfigValid;
    
    /**
     * 验证错误列表
     */
    @Builder.Default
    private List<ImportResult.ValidationError> errors = new ArrayList<>();
    
    /**
     * 验证警告列表
     */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
    
    /**
     * 创建成功结果
     */
    public static ValidationResult success() {
        return ValidationResult.builder()
                .valid(true)
                .fileFormatValid(true)
                .integrityValid(true)
                .signatureValid(true)
                .bpmnSyntaxValid(true)
                .dataTableValid(true)
                .formConfigValid(true)
                .build();
    }
    
    /**
     * 创建失败结果
     */
    public static ValidationResult failure(List<ImportResult.ValidationError> errors) {
        return ValidationResult.builder()
                .valid(false)
                .errors(errors)
                .build();
    }
    
    /**
     * 添加错误
     */
    public void addError(String type, String field, String message) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        errors.add(ImportResult.ValidationError.builder()
                .type(type)
                .field(field)
                .message(message)
                .build());
        this.valid = false;
    }
    
    /**
     * 添加警告
     */
    public void addWarning(String warning) {
        if (warnings == null) {
            warnings = new ArrayList<>();
        }
        warnings.add(warning);
    }
}
