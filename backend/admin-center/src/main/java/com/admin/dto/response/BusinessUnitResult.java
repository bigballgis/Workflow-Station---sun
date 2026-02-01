package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 业务单元操作结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessUnitResult {
    
    private boolean success;
    private String businessUnitId;
    private String code;
    private String message;
    
    public static BusinessUnitResult success(String businessUnitId, String code) {
        return BusinessUnitResult.builder()
                .success(true)
                .businessUnitId(businessUnitId)
                .code(code)
                .message("操作成功")
                .build();
    }
    
    public static BusinessUnitResult failure(String message) {
        return BusinessUnitResult.builder()
                .success(false)
                .message(message)
                .build();
    }
}
