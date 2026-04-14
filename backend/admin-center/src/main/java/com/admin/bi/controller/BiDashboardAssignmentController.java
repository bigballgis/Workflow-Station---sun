package com.admin.bi.controller;

import com.admin.bi.dto.request.DashboardAssignmentCreateRequest;
import com.admin.bi.dto.response.DashboardAssignmentResponse;
import com.admin.bi.dto.response.UserDashboardResponse;
import com.admin.bi.enums.AssignmentTargetType;
import com.admin.bi.service.BiDashboardAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Dashboard 分配管理控制器
 */
@RestController
@RequestMapping("/bi/assignments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Dashboard 分配管理", description = "Dashboard 分配的创建、查询、更新、删除及用户有效 Dashboard 列表接口")
public class BiDashboardAssignmentController {

    private final BiDashboardAssignmentService assignmentService;

    @PostMapping
    @Operation(summary = "创建分配记录", description = "将 Dashboard 分配给 User、Role 或 Business Unit")
    public ResponseEntity<DashboardAssignmentResponse> createAssignment(
            @RequestBody @Valid DashboardAssignmentCreateRequest request,
            @RequestHeader("X-User-Id") String userId) {
        log.info("User {} creating assignment for dashboard {}", userId, request.getDashboardId());
        DashboardAssignmentResponse response = assignmentService.createAssignment(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "分页查询分配列表", description = "支持按 targetType 和 dashboardTitle 筛选")
    public ResponseEntity<Page<DashboardAssignmentResponse>> listAssignments(
            @RequestParam(required = false) AssignmentTargetType targetType,
            @RequestParam(required = false) String dashboardTitle,
            Pageable pageable) {
        Page<DashboardAssignmentResponse> page = assignmentService.listAssignments(targetType, dashboardTitle, pageable);
        return ResponseEntity.ok(page);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新分配记录", description = "更新指定分配记录的字段")
    public ResponseEntity<DashboardAssignmentResponse> updateAssignment(
            @PathVariable String id,
            @RequestBody @Valid DashboardAssignmentCreateRequest request,
            @RequestHeader("X-User-Id") String userId) {
        log.info("User {} updating assignment {}", userId, id);
        DashboardAssignmentResponse response = assignmentService.updateAssignment(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除分配记录", description = "删除指定的分配记录")
    public ResponseEntity<Void> deleteAssignment(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String userId) {
        log.info("User {} deleting assignment {}", userId, id);
        assignmentService.deleteAssignment(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/user/{userId}")
    @Operation(
        summary = "获取用户有效 Dashboard 列表",
        description = "合并 User/Role/BU 维度分配，去重后按 displayOrder 排序。" +
                      "传入 activeBusinessUnitId 时，BU 维度仅检索该 BU 的分配记录。"
    )
    public ResponseEntity<List<UserDashboardResponse>> getUserDashboards(
            @PathVariable String userId,
            @RequestParam(required = false) String activeBusinessUnitId) {
        List<UserDashboardResponse> dashboards = assignmentService.getUserDashboards(userId, activeBusinessUnitId);
        return ResponseEntity.ok(dashboards);
    }
}
