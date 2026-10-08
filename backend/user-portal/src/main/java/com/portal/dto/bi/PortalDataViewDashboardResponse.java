package com.portal.dto.bi;

public record PortalDataViewDashboardResponse(
        String dashboardId,
        String dashboardTitle,
        String description,
        String embedId) {
}
