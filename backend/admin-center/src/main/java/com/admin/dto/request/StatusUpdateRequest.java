package com.admin.dto.request;

import com.admin.enums.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 状态更新请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusUpdateRequest {
    
    @NotNull(message = "状态不能为空")
    private UserStatus status;
    
    private String reason;
}
