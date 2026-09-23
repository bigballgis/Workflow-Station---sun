package com.portal.dto.bi;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record PortalGuestTokenRequest(
        @NotBlank(message = "Dashboard ID is required") String dashboardId,
        @Positive(message = "Data View ID must be positive") Long dataViewId,
        String activeBusinessUnitId) {

    public PortalGuestTokenRequest withActiveBusinessUnitId(String businessUnitId) {
        return new PortalGuestTokenRequest(dashboardId, dataViewId, businessUnitId);
    }
}
