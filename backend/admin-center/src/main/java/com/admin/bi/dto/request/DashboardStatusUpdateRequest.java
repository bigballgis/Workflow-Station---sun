package com.admin.bi.dto.request;

import com.admin.bi.enums.DashboardStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dashboard 状态更新请求
 */
@Data
@NoArgsConstructor
public class DashboardStatusUpdateRequest {

    /** 目标状态 */
    @NotNull(message = "Status is required")
    private DashboardStatus status;
}
