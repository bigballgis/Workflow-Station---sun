package com.admin.bi.controller;

import com.admin.bi.component.BiDashboardListQueryComponent;
import com.admin.bi.dto.request.DashboardRegistryUpdateRequest;
import com.admin.bi.dto.request.DashboardStatusUpdateRequest;
import com.admin.bi.dto.response.DashboardRegistryResponse;
import com.admin.bi.dto.response.SyncResultResponse;
import com.admin.bi.enums.DashboardStatus;
import com.admin.bi.service.BiDashboardRegistryService;
import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.BiDashboardListQueryRequest;
import com.platform.security.util.SecurityContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Dashboard registry management controller
 */
@RestController
@RequestMapping("/bi/dashboards")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Dashboard Registry Management", description = "Dashboard sync, query, update, status toggle, delete and other endpoints")
public class BiDashboardRegistryController {

    private final BiDashboardRegistryService dashboardRegistryService;
    private final BiDashboardListQueryComponent dashboardListQueryComponent;

    @PostMapping("/sync")
    @Operation(summary = "Manually sync Dashboards", description = "Immediately execute a Sync_Operation and return sync result summary")
    public ResponseEntity<SyncResultResponse> syncDashboards() {
        String userId = SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException("Unauthenticated user"));
        log.info("User {} triggered manual dashboard sync", userId);
        SyncResultResponse result = dashboardRegistryService.syncDashboards();
        return ResponseEntity.ok(result);
    }

    @GetMapping
    @Operation(summary = "List Dashboards with pagination", description = "Supports filtering by title, tags, and status")
    public ResponseEntity<Page<DashboardRegistryResponse>> listDashboards(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) DashboardStatus status,
            Pageable pageable) {
        Page<DashboardRegistryResponse> page = dashboardRegistryService.listDashboards(title, tags, status, pageable);
        return ResponseEntity.ok(page);
    }

    @PostMapping("/query")
    @Operation(summary = "Query Dashboards (true paging; column filters and sort)")
    public ResponseEntity<AdminListPage<DashboardRegistryResponse>> queryDashboards(
            @RequestBody @Valid BiDashboardListQueryRequest request) {
        return ResponseEntity.ok(dashboardListQueryComponent.query(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Dashboard details")
    public ResponseEntity<DashboardRegistryResponse> getDashboard(@PathVariable String id) {
        DashboardRegistryResponse response = dashboardRegistryService.getDashboard(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update local extension fields", description = "Only allows updating Tags and Is_Default_Landing")
    public ResponseEntity<DashboardRegistryResponse> updateDashboard(
            @PathVariable String id,
            @RequestBody @Valid DashboardRegistryUpdateRequest request) {
        String userId = SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException("Unauthenticated user"));
        log.info("User {} updating dashboard {}", userId, id);
        DashboardRegistryResponse response = dashboardRegistryService.updateDashboard(id, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Toggle Dashboard status", description = "Enable (MANUAL_INACTIVE → ACTIVE) or disable (ACTIVE → MANUAL_INACTIVE)")
    public ResponseEntity<DashboardRegistryResponse> updateDashboardStatus(
            @PathVariable String id,
            @RequestBody @Valid DashboardStatusUpdateRequest request) {
        String userId = SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException("Unauthenticated user"));
        log.info("User {} changing dashboard {} status to {}", userId, id, request.getStatus());
        DashboardRegistryResponse response;
        if (request.getStatus() == DashboardStatus.ACTIVE) {
            response = dashboardRegistryService.enableDashboard(id);
        } else {
            response = dashboardRegistryService.disableDashboard(id);
        }
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Dashboard", description = "Rejects deletion when there are associated assignments")
    public ResponseEntity<Void> deleteDashboard(
            @PathVariable String id) {
        String userId = SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new RuntimeException("Unauthenticated user"));
        log.info("User {} deleting dashboard {}", userId, id);
        dashboardRegistryService.deleteDashboard(id);
        return ResponseEntity.noContent().build();
    }
}
