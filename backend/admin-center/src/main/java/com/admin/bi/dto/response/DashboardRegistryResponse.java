package com.admin.bi.dto.response;

import com.admin.bi.enums.DashboardStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Dashboard 注册表响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardRegistryResponse {

    private String id;
    private String dashboardTitle;
    private String description;
    private UUID embedId;
    private UUID supersetDashboardUuid;
    private Integer supersetDashboardId;
    private String tags;
    private Boolean isDefaultLanding;
    private DashboardStatus status;
    private LocalDateTime lastSyncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
