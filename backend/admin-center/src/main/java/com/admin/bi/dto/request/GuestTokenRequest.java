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

    /**
     * User Portal workspace context. Audience assignments for BUSINESS_UNIT and
     * BU-scoped roles are evaluated against this exact context. The value is
     * accepted only after the server verifies that the delegated user belongs
     * to the business unit.
     */
    private String activeBusinessUnitId;

    /**
     * Optional User Portal Data View context, named by function unit code and View name (both or
     * neither). When present, authorization is based on the dashboard-to-View binding and the
     * current user's access to that View in the unit's active release. When absent, the Audience
     * Assignment authorization path is used.
     */
    private String dataViewFunctionUnitCode;

    private String dataViewName;
}
