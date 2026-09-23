package com.portal.dto.bi;

public record PortalUserDashboardResponse(
        String dashboardId,
        String dashboardTitle,
        String description,
        String embedId,
        String layoutMode,
        Integer displayOrder,
        Boolean isDefault) {
}
