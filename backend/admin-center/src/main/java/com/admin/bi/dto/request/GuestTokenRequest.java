package com.admin.bi.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Guest Token 获取请求
 */
@Data
@NoArgsConstructor
public class GuestTokenRequest {

    /** Dashboard ID */
    @NotBlank(message = "Dashboard ID is required")
    private String dashboardId;
}
