package com.portal.controller;

import com.portal.component.DashboardComponent;
import com.portal.dto.ApiResponse;
import com.portal.dto.DashboardOverview;
import com.portal.dto.TeamRequestsResponse;
import com.portal.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Dashboard API
 */
@Tag(name = "Dashboard", description = "Dashboard overview and statistics")
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardComponent dashboardComponent;

    @Operation(summary = "Get Dashboard overview data")
    @GetMapping("/overview")
    public ApiResponse<DashboardOverview> getDashboardOverview(
            @CurrentUserId String userId) {
        DashboardOverview overview = dashboardComponent.getDashboardOverview(userId);
        return ApiResponse.success(overview);
    }

    @Operation(summary = "Get task overview")
    @GetMapping("/task-overview")
    public ApiResponse<DashboardOverview.TaskOverview> getTaskOverview(
            @CurrentUserId String userId) {
        DashboardOverview.TaskOverview overview = dashboardComponent.getTaskOverview(userId);
        return ApiResponse.success(overview);
    }

    @Operation(summary = "Get process overview")
    @GetMapping("/process-overview")
    public ApiResponse<DashboardOverview.ProcessOverview> getProcessOverview(
            @CurrentUserId String userId) {
        DashboardOverview.ProcessOverview overview = dashboardComponent.getProcessOverview(userId);
        return ApiResponse.success(overview);
    }

    @Operation(summary = "Get personal performance")
    @GetMapping("/performance")
    public ApiResponse<DashboardOverview.PerformanceOverview> getPerformanceOverview(
            @CurrentUserId String userId) {
        DashboardOverview.PerformanceOverview overview = dashboardComponent.getPerformanceOverview(userId);
        return ApiResponse.success(overview);
    }

    @Operation(summary = "Get task trend data")
    @GetMapping("/task-trend")
    public ApiResponse<Map<String, Object>> getTaskTrendData(
            @CurrentUserId String userId,
            @RequestParam(defaultValue = "30") int days) {
        Map<String, Object> data = dashboardComponent.getTaskTrendData(userId, days);
        return ApiResponse.success(data);
    }

    @Operation(summary = "Get process statistics data")
    @GetMapping("/process-statistics")
    public ApiResponse<Map<String, Object>> getProcessStatisticsData(
            @CurrentUserId String userId) {
        Map<String, Object> data = dashboardComponent.getProcessStatisticsData(userId);
        return ApiResponse.success(data);
    }

    @Operation(summary = "Get team request list")
    @GetMapping("/team-requests")
    public ApiResponse<TeamRequestsResponse> getTeamRequests(
            @CurrentUserId String userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        TeamRequestsResponse response = dashboardComponent.getTeamRequests(userId, status, page, size);
        return ApiResponse.success(response);
    }
}
