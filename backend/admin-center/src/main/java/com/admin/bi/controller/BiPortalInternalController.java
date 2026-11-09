package com.admin.bi.controller;

import com.admin.bi.dto.request.GuestTokenRequest;
import com.admin.bi.dto.response.DataViewDashboardResponse;
import com.admin.bi.dto.response.GuestTokenResponse;
import com.admin.bi.dto.response.UserDashboardResponse;
import com.admin.bi.service.BiDashboardAssignmentService;
import com.admin.bi.service.BiDataViewAssignmentService;
import com.admin.bi.service.BiGuestTokenService;
import com.admin.config.InternalServiceAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.admin.security.AdminAccess;
import com.admin.security.AdminAudience;

/** Internal BI facade used only by the User Portal backend. */
@RestController
@RequestMapping("/internal/bi")
@RequiredArgsConstructor
@AdminAccess(AdminAudience.SERVICE)
public class BiPortalInternalController {

    private final InternalServiceAuthenticator internalServiceAuthenticator;
    private final BiDashboardAssignmentService dashboardAssignmentService;
    private final BiDataViewAssignmentService dataViewAssignmentService;
    private final BiGuestTokenService guestTokenService;

    @GetMapping("/dashboards")
    public ResponseEntity<List<UserDashboardResponse>> getDashboards(
            HttpServletRequest servletRequest,
            @RequestParam(required = false) String activeBusinessUnitId) {
        String userId = internalServiceAuthenticator.requireDelegatedUserId(servletRequest);
        return ResponseEntity.ok(
                dashboardAssignmentService.getUserDashboards(userId, activeBusinessUnitId));
    }

    @AdminAccess(AdminAudience.SERVICE)
    @PostMapping("/guest-token")
    public ResponseEntity<GuestTokenResponse> getGuestToken(
            HttpServletRequest servletRequest,
            @RequestBody @Valid GuestTokenRequest request) {
        String userId = internalServiceAuthenticator.requireDelegatedUserId(servletRequest);
        return ResponseEntity.ok(guestTokenService.getGuestToken(userId, request));
    }

    /** A View is named by function unit code and View name (names may hold spaces and slashes: query, not path). */
    @GetMapping("/data-views/dashboards")
    public ResponseEntity<List<DataViewDashboardResponse>> getDataViewDashboards(
            HttpServletRequest servletRequest,
            @RequestParam String functionUnitCode,
            @RequestParam String viewName) {
        String userId = internalServiceAuthenticator.requireDelegatedUserId(servletRequest);
        return ResponseEntity.ok(dataViewAssignmentService.getDashboardsForView(userId, functionUnitCode, viewName));
    }
}
