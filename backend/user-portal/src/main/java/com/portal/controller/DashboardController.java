package com.portal.controller;

import com.portal.component.DashboardComponent;
import com.portal.dto.ApiResponse;
import com.portal.dto.DashboardOverview;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Dashboard API
 */
@Tag(name = "工作台", description = "Dashboard概览和统计数据")
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardComponent dashboardComponent;

    @Operation(summary = "获取Dashboard概览数据")
    @GetMapping("/overview")
    public ApiResponse<DashboardOverview> getDashboardOverview(
            @RequestHeader("X-User-Id") String userId) {
        DashboardOverview overview = dashboardComponent.getDashboardOverview(userId);
        return ApiResponse.success(overview);
    }

    @Operation(summary = "获取任务概览")
    @GetMapping("/task-overview")
    public ApiResponse<DashboardOverview.TaskOverview> getTaskOverview(
            @RequestHeader("X-User-Id") String userId) {
        DashboardOverview.TaskOverview overview = dashboardComponent.getTaskOverview(userId);
        return ApiResponse.success(overview);
    }

    @Operation(summary = "获取流程概览")
    @GetMapping("/process-overview")
    public ApiResponse<DashboardOverview.ProcessOverview> getProcessOverview(
            @RequestHeader("X-User-Id") String userId) {
        DashboardOverview.ProcessOverview overview = dashboardComponent.getProcessOverview(userId);
        return ApiResponse.success(overview);
    }

    @Operation(summary = "获取个人绩效")
    @GetMapping("/performance")
    public ApiResponse<DashboardOverview.PerformanceOverview> getPerformanceOverview(
            @RequestHeader("X-User-Id") String userId) {
        DashboardOverview.PerformanceOverview overview = dashboardComponent.getPerformanceOverview(userId);
        return ApiResponse.success(overview);
    }

    @Operation(summary = "获取任务趋势数据")
    @GetMapping("/task-trend")
    public ApiResponse<Map<String, Object>> getTaskTrendData(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "30") int days) {
        Map<String, Object> data = dashboardComponent.getTaskTrendData(userId, days);
        return ApiResponse.success(data);
    }

    @Operation(summary = "获取流程统计数据")
    @GetMapping("/process-statistics")
    public ApiResponse<Map<String, Object>> getProcessStatisticsData(
            @RequestHeader("X-User-Id") String userId) {
        Map<String, Object> data = dashboardComponent.getProcessStatisticsData(userId);
        return ApiResponse.success(data);
    }
}
