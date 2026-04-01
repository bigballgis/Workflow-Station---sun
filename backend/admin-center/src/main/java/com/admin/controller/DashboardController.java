package com.admin.controller;

import com.admin.component.DashboardComponent;
import com.admin.dto.response.DashboardStats;
import com.admin.dto.response.RecentActivity;
import com.admin.dto.response.UserTrend;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Dashboard 控制器 - 管理员中心仪表盘统计接口
 */
@Slf4j
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "管理员中心仪表盘统计接口")
public class DashboardController {

    private final DashboardComponent dashboardComponent;

    @GetMapping("/stats")
    @Operation(summary = "获取统计数据", description = "获取用户、业务单元、角色等统计数据")
    public ResponseEntity<DashboardStats> getStats() {
        log.info("Getting dashboard stats");
        return ResponseEntity.ok(dashboardComponent.getStats());
    }

    @GetMapping("/activities")
    @Operation(summary = "获取最近活动", description = "获取最近的审计日志活动")
    public ResponseEntity<List<RecentActivity>> getRecentActivities(
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        log.info("Getting recent activities, limit: {}", limit);
        return ResponseEntity.ok(dashboardComponent.getRecentActivities(limit));
    }

    @GetMapping("/user-trends")
    @Operation(summary = "获取用户趋势", description = "获取最近N天的用户活跃趋势")
    public ResponseEntity<List<UserTrend>> getUserTrends(
            @RequestParam(defaultValue = "7") int days) {
        log.info("Getting user trends for {} days", days);
        return ResponseEntity.ok(dashboardComponent.getUserTrends(days));
    }
}
