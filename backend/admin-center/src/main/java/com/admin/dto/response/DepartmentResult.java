package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 部门操作结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentResult {
    
    private boolean success;
    private String departmentId;
    private String code;
    private String message;
    
    public static DepartmentResult success(String departmentId, String code) {
        return DepartmentResult.builder()
                .success(true)
                .departmentId(departmentId)
                .code(code)
                .message("操作成功")
                .build();
    }
    
    public static DepartmentResult failure(String message) {
        return DepartmentResult.builder()
                .success(false)
                .message(message)
                .build();
    }
}
