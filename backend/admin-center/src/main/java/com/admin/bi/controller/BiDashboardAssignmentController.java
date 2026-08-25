package com.admin.bi.controller;

import com.admin.bi.component.BiAssignmentListQueryComponent;
import com.admin.bi.dto.request.DashboardAssignmentCreateRequest;
import com.admin.bi.dto.response.DashboardAssignmentResponse;
import com.admin.bi.dto.response.UserDashboardResponse;
import com.admin.bi.enums.AssignmentTargetType;
import com.admin.bi.service.BiDashboardAssignmentService;
import com.admin.dto.list.AdminListPage;
import com.admin.dto.request.BiAssignmentListQueryRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.platform.security.util.SecurityContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Dashboard assignment management controller
 */
@RestController
@RequestMapping("/bi/assignments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Dashboard Assignment Management", description = "Dashboard assignment CRUD and user effective dashboard listing")
public class BiDashboardAssignmentController {

    private final BiDashboardAssignmentService assignmentService;
    private final BiAssignmentListQueryComponent assignmentListQueryComponent;

    @PostMapping
    @Operation(summary = "Create assignment record", description = "Assign a Dashboard to a User, Role, or Business Unit")
    public ResponseEntity<DashboardAssignmentResponse> createAssignment(
            @RequestBody @Valid DashboardAssignmentCreateRequest request) {
        log.info("User {} creating assignment for dashboard {}", SecurityContextUtils.getCurrentUserId(), request.getDashboardId());
        DashboardAssignmentResponse response = assignmentService.createAssignment(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "List assignments with pagination", description = "Supports filtering by targetType and dashboardTitle")
    public ResponseEntity<Page<DashboardAssignmentResponse>> listAssignments(
            @RequestParam(required = false) AssignmentTargetType targetType,
            @RequestParam(required = false) String dashboardTitle,
            Pageable pageable) {
        Page<DashboardAssignmentResponse> page = assignmentService.listAssignments(targetType, dashboardTitle, pageable);
        return ResponseEntity.ok(page);
    }

    @PostMapping("/query")
    @Operation(summary = "Query assignments (true paging; column filters, sort and grouping)")
    public ResponseEntity<AdminListPage<DashboardAssignmentResponse>> queryAssignments(
            @RequestBody BiAssignmentListQueryRequest request) {
        return ResponseEntity.ok(assignmentListQueryComponent.query(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update assignment record", description = "Update fields of a specified assignment record")
    public ResponseEntity<DashboardAssignmentResponse> updateAssignment(
            @PathVariable String id,
            @RequestBody @Valid DashboardAssignmentCreateRequest request) {
        log.info("User {} updating assignment {}", SecurityContextUtils.getCurrentUserId(), id);
        DashboardAssignmentResponse response = assignmentService.updateAssignment(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete assignment record", description = "Delete a specified assignment record")
    public ResponseEntity<Void> deleteAssignment(
            @PathVariable String id) {
        log.info("User {} deleting assignment {}", SecurityContextUtils.getCurrentUserId(), id);
        assignmentService.deleteAssignment(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/user/{userId}")
    @Operation(
        summary = "Get user's effective Dashboard list",
        description = "Merges User/Role/BU dimension assignments, deduplicates, and sorts by displayOrder. " +
                      "When activeBusinessUnitId is provided, only that BU's assignment records are retrieved for the BU dimension."
    )
    public ResponseEntity<List<UserDashboardResponse>> getUserDashboards(
            @PathVariable String userId,
            @RequestParam(required = false) String activeBusinessUnitId) {
        List<UserDashboardResponse> dashboards = assignmentService.getUserDashboards(userId, activeBusinessUnitId);
        return ResponseEntity.ok(dashboards);
    }
}
