package com.admin.bi.controller;

import com.admin.bi.component.BiAdminAccessGuard;
import com.admin.bi.dto.request.DataViewAssignmentRequest;
import com.admin.bi.dto.request.DataViewAssignmentBatchRequest;
import com.admin.bi.dto.response.DataViewAssignmentResponse;
import com.admin.bi.dto.response.DataViewDashboardResponse;
import com.admin.bi.dto.response.DataViewFunctionUnitOptionResponse;
import com.admin.bi.dto.response.DataViewTableOptionResponse;
import com.admin.bi.service.BiDataViewAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import com.admin.security.AdminAccess;
import com.admin.security.AdminAudience;
import com.admin.security.AdminOwner;
import com.admin.security.AdminTeamScope;

@RestController
@RequestMapping("/bi/data-view-assignments")
@RequiredArgsConstructor
@Tag(name = "Data View Assignment Management", description = "Bind BI dashboards to User Portal data-view tables")
@AdminAccess(AdminAudience.ADMIN_READ)
public class BiDataViewAssignmentController {

    private final BiDataViewAssignmentService assignmentService;
    private final BiAdminAccessGuard adminAccessGuard;
    private final com.admin.bi.component.BiAssignmentConfigurationComponent configuration;
    private final com.admin.bi.component.BiDataViewAssignmentListQueryComponent listQuery;
    private final AdminTeamScope teamScope;

    @AdminAccess(AdminAudience.TEAM_READ)
    @PostMapping("/query")
    public ResponseEntity<com.admin.dto.list.AdminListPage<DataViewAssignmentResponse>> query(
            @RequestBody @Valid com.admin.dto.request.BiDataViewAssignmentListQueryRequest request) {
        adminAccessGuard.requireAdminUserId();
        return ResponseEntity.ok(listQuery.query(request));
    }

    @AdminAccess(value = AdminAudience.TEAM_WRITE, owner = AdminOwner.DATA_VIEW_ASSIGNMENT, ownerVariable = "id")
    @PutMapping("/{id}/enabled")
    public ResponseEntity<com.platform.common.dto.ApiResponse<DataViewAssignmentResponse>> setEnabled(
            @PathVariable String id,
            @RequestBody @Valid com.admin.bi.dto.request.AssignmentEnabledRequest request) {
        return ResponseEntity.ok(com.platform.common.dto.ApiResponse.success(
                configuration.setDataViewEnabled(id, request.enabled())));
    }

    @AdminAccess(AdminAudience.TEAM_WRITE)
    @PostMapping
    @Operation(summary = "Create a Data View Assignment")
    public ResponseEntity<DataViewAssignmentResponse> create(
            @RequestBody @Valid DataViewAssignmentRequest request) {
        adminAccessGuard.requireAdminUserId();
        // A Technical Lead binds dashboards only to Views of the teams they lead; any dashboard (D-15, AC-32).
        requireTeamUnit(request.getFunctionUnitCode());
        return ResponseEntity.ok(assignmentService.create(request));
    }

    @AdminAccess(AdminAudience.TEAM_WRITE)
    @PostMapping("/batch")
    @Operation(summary = "Assign several dashboards to one Data View table")
    public ResponseEntity<List<DataViewAssignmentResponse>> createBatch(
            @RequestBody @Valid DataViewAssignmentBatchRequest request) {
        adminAccessGuard.requireAdminUserId();
        requireTeamUnit(request.getFunctionUnitCode());
        return ResponseEntity.ok(assignmentService.createBatch(request));
    }

    @GetMapping
    @Operation(summary = "List Data View Assignments")
    public ResponseEntity<Page<DataViewAssignmentResponse>> list(
            @RequestParam(required = false) String dashboardTitle,
            @RequestParam(required = false) String functionUnitCode,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String viewName,
            Pageable pageable) {
        adminAccessGuard.requireAdminUserId();
        return ResponseEntity.ok(assignmentService.list(dashboardTitle, functionUnitCode, enabled, viewName, pageable));
    }

    @AdminAccess(value = AdminAudience.TEAM_WRITE, owner = AdminOwner.DATA_VIEW_ASSIGNMENT, ownerVariable = "id")
    @PutMapping("/{id}")
    @Operation(summary = "Update a Data View Assignment")
    public ResponseEntity<DataViewAssignmentResponse> update(
            @PathVariable String id,
            @RequestBody @Valid DataViewAssignmentRequest request) {
        adminAccessGuard.requireAdminUserId();
        // The path's row was checked before the handler; the unit it moves to must be the caller's too.
        requireTeamUnit(request.getFunctionUnitCode());
        return ResponseEntity.ok(assignmentService.update(id, request));
    }

    @AdminAccess(value = AdminAudience.TEAM_WRITE, owner = AdminOwner.DATA_VIEW_ASSIGNMENT, ownerVariable = "id")
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a Data View Assignment")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        adminAccessGuard.requireAdminUserId();
        assignmentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** A Technical Lead is offered the units of the teams they lead: the code from this module's query (D-46). */
    @AdminAccess(AdminAudience.TEAM_READ)
    @GetMapping("/function-units")
    @Operation(summary = "List function units whose active release publishes Data Views")
    public ResponseEntity<List<DataViewFunctionUnitOptionResponse>> listFunctionUnits() {
        adminAccessGuard.requireAdminUserId();
        Set<String> visible = teamScope.visibleFunctionUnitCodes();
        return ResponseEntity.ok(assignmentService.listFunctionUnits().stream()
                .filter(unit -> visible == null || visible.contains(unit.code()))
                .toList());
    }

    @AdminAccess(value = AdminAudience.TEAM_READ, owner = AdminOwner.FUNCTION_UNIT_CODE,
            ownerVariable = "functionUnitCode")
    @GetMapping("/function-units/{functionUnitCode}/tables")
    @Operation(summary = "List request/sub tables that published Data Views of the active release list")
    public ResponseEntity<List<DataViewTableOptionResponse>> listTables(
            @PathVariable String functionUnitCode) {
        adminAccessGuard.requireAdminUserId();
        return ResponseEntity.ok(assignmentService.listTables(functionUnitCode));
    }

    @AdminAccess(value = AdminAudience.TEAM_READ, owner = AdminOwner.FUNCTION_UNIT_CODE,
            ownerVariable = "functionUnitCode")
    @GetMapping("/function-units/{functionUnitCode}/tables/{tableName}/views")
    public ResponseEntity<com.platform.common.dto.ApiResponse<List<com.admin.bi.dto.response.DataViewOptionResponse>>> listViews(
            @PathVariable String functionUnitCode, @PathVariable String tableName) {
        return ResponseEntity.ok(com.platform.common.dto.ApiResponse.success(
                configuration.listViews(functionUnitCode, tableName)));
    }

    private void requireTeamUnit(String functionUnitCode) {
        if (functionUnitCode != null) {
            teamScope.require(AdminOwner.FUNCTION_UNIT_CODE, functionUnitCode, true);
        }
    }

    /** View names may hold spaces and slashes, so the View is named in the query, not the path. */
    @GetMapping("/views/dashboards")
    @Operation(summary = "List the dashboards bound to an accessible User Portal Data View")
    public ResponseEntity<List<DataViewDashboardResponse>> getDashboardsForView(
            @RequestParam String functionUnitCode, @RequestParam String viewName) {
        String userId = adminAccessGuard.requireAdminUserId();
        return ResponseEntity.ok(assignmentService.getDashboardsForView(userId, functionUnitCode, viewName));
    }
}
