package com.admin.bi.controller;

import com.admin.bi.dto.request.DashboardRegistryUpdateRequest;
import com.admin.bi.dto.request.DashboardStatusUpdateRequest;
import com.admin.bi.dto.response.DashboardRegistryResponse;
import com.admin.bi.dto.response.SyncResultResponse;
import com.admin.bi.enums.DashboardStatus;
import com.admin.bi.service.BiDashboardRegistryService;
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
 * Dashboard 注册表管理控制器
 */
@RestController
@RequestMapping("/bi/dashboards")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Dashboard 注册表管理", description = "Dashboard 同步、查询、更新、状态切换、删除等接口")
public class BiDashboardRegistryController {

    private final BiDashboardRegistryService dashboardRegistryService;

    @PostMapping("/sync")
    @Operation(summary = "手动同步 Dashboard", description = "立即执行一次 Sync_Operation 并返回同步结果摘要")
    public ResponseEntity<SyncResultResponse> syncDashboards(
            @RequestHeader("X-User-Id") String userId) {
        log.info("User {} triggered manual dashboard sync", userId);
        SyncResultResponse result = dashboardRegistryService.syncDashboards();
        return ResponseEntity.ok(result);
    }

    @GetMapping
    @Operation(summary = "分页查询 Dashboard 列表", description = "支持按 title、tags、status 筛选")
    public ResponseEntity<Page<DashboardRegistryResponse>> listDashboards(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) DashboardStatus status,
            Pageable pageable) {
        Page<DashboardRegistryResponse> page = dashboardRegistryService.listDashboards(title, tags, status, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取 Dashboard 详情")
    public ResponseEntity<DashboardRegistryResponse> getDashboard(@PathVariable String id) {
        DashboardRegistryResponse response = dashboardRegistryService.getDashboard(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新本地扩展字段", description = "仅允许更新 Tags 和 Is_Default_Landing")
    public ResponseEntity<DashboardRegistryResponse> updateDashboard(
            @PathVariable String id,
            @RequestBody @Valid DashboardRegistryUpdateRequest request,
            @RequestHeader("X-User-Id") String userId) {
        log.info("User {} updating dashboard {}", userId, id);
        DashboardRegistryResponse response = dashboardRegistryService.updateDashboard(id, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "切换 Dashboard 状态", description = "启用（MANUAL_INACTIVE → ACTIVE）或禁用（ACTIVE → MANUAL_INACTIVE）")
    public ResponseEntity<DashboardRegistryResponse> updateDashboardStatus(
            @PathVariable String id,
            @RequestBody @Valid DashboardStatusUpdateRequest request,
            @RequestHeader("X-User-Id") String userId) {
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
    @Operation(summary = "删除 Dashboard", description = "有关联分配时拒绝删除")
    public ResponseEntity<Void> deleteDashboard(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String userId) {
        log.info("User {} deleting dashboard {}", userId, id);
        dashboardRegistryService.deleteDashboard(id);
        return ResponseEntity.noContent().build();
    }
}
