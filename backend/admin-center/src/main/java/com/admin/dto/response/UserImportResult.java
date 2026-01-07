package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户批量导入结果
 * Validates: Requirements 6.3, 6.4
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserImportResult {
    
    /**
     * 总记录数
     */
    private int total;
    
    /**
     * 成功数量
     */
    private int success;
    
    /**
     * 失败数量
     */
    private int failed;
    
    /**
     * 错误详情列表
     */
    @Builder.Default
    private List<ImportError> errors = new ArrayList<>();
    
    /**
     * 导入错误详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportError {
        /**
         * 行号（从1开始，不含表头）
         */
        private int row;
        
        /**
         * 字段名
         */
        private String field;
        
        /**
         * 错误消息
         */
        private String message;
        
        /**
         * 原始值
         */
        private String value;
    }
    
    /**
     * 创建成功结果
     */
    public static UserImportResult success(int total) {
        return UserImportResult.builder()
                .total(total)
                .success(total)
                .failed(0)
                .build();
    }
    
    /**
     * 创建部分成功结果
     */
    public static UserImportResult partial(int total, int success, List<ImportError> errors) {
        return UserImportResult.builder()
                .total(total)
                .success(success)
                .failed(total - success)
                .errors(errors)
                .build();
    }
    
    /**
     * 创建全部失败结果
     */
    public static UserImportResult failure(int total, List<ImportError> errors) {
        return UserImportResult.builder()
                .total(total)
                .success(0)
                .failed(total)
                .errors(errors)
                .build();
    }
}
