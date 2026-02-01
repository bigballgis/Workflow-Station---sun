package com.admin.controller;

import com.admin.dto.response.DashboardStats;
import com.admin.dto.response.RecentActivity;
import com.admin.dto.response.UserTrend;
import com.admin.entity.AuditLog;
import com.admin.repository.AuditLogRepository;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.RoleRepository;
import com.admin.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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

    private final UserRepository userRepository;
    private final BusinessUnitRepository businessUnitRepository;
    private final RoleRepository roleRepository;
    private final AuditLogRepository auditLogRepository;

    @GetMapping("/stats")
    @Operation(summary = "获取统计数据", description = "获取用户、业务单元、角色等统计数据")
    public ResponseEntity<DashboardStats> getStats() {
        log.info("Getting dashboard stats");
        
        long totalUsers = userRepository.count();
        long totalBusinessUnits = businessUnitRepository.count();
        long totalRoles = roleRepository.count();
        
        // 计算在线用户数（最近30分钟有登录活动的用户）
        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);
        long onlineUsers = userRepository.countByLastLoginAtAfter(thirtyMinutesAgo);
        
        // 今日登录数
        Instant todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        long todayLogins = auditLogRepository.countByActionAndTimestampAfter(
            com.admin.enums.AuditAction.USER_LOGIN, todayStart);
        
        // 今日新增用户
        LocalDateTime todayStartLocal = LocalDate.now().atStartOfDay();
        long todayNewUsers = userRepository.countByCreatedAtAfter(todayStartLocal);
        
        DashboardStats stats = DashboardStats.builder()
            .totalUsers(totalUsers)
            .totalBusinessUnits(totalBusinessUnits)
            .totalRoles(totalRoles)
            .onlineUsers(onlineUsers)
            .todayLogins(todayLogins)
            .todayNewUsers(todayNewUsers)
            .activeProcesses(0L) // 需要从工作流引擎获取
            .pendingTasks(0L) // 需要从工作流引擎获取
            .build();
        
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/activities")
    @Operation(summary = "获取最近活动", description = "获取最近的审计日志活动")
    public ResponseEntity<List<RecentActivity>> getRecentActivities(
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Getting recent activities, limit: {}", limit);
        
        List<AuditLog> logs = auditLogRepository.findAll(PageRequest.of(0, limit)).getContent();
        
        List<RecentActivity> activities = logs.stream()
            .map(this::toRecentActivity)
            .toList();
        
        return ResponseEntity.ok(activities);
    }

    @GetMapping("/user-trends")
    @Operation(summary = "获取用户趋势", description = "获取最近N天的用户活跃趋势")
    public ResponseEntity<List<UserTrend>> getUserTrends(
            @RequestParam(defaultValue = "7") int days) {
        log.info("Getting user trends for {} days", days);
        
        List<UserTrend> trends = new ArrayList<>();
        LocalDate today = LocalDate.now();
        
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Instant dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant dayEnd = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            LocalDateTime dayStartLocal = date.atStartOfDay();
            LocalDateTime dayEndLocal = date.plusDays(1).atStartOfDay();
            
            // 当天活跃用户数（有登录记录的用户）
            long activeUsers = auditLogRepository.countDistinctUsersByActionAndTimestampBetween(
                com.admin.enums.AuditAction.USER_LOGIN, dayStart, dayEnd);
            
            // 当天新增用户数
            long newUsers = userRepository.countByCreatedAtBetween(dayStartLocal, dayEndLocal);
            
            // 当天登录次数
            long loginCount = auditLogRepository.countByActionAndTimestampAfter(
                com.admin.enums.AuditAction.USER_LOGIN, dayStart);
            
            trends.add(UserTrend.builder()
                .date(date.toString())
                .activeUsers(activeUsers)
                .newUsers(newUsers)
                .loginCount(loginCount)
                .build());
        }
        
        return ResponseEntity.ok(trends);
    }

    private RecentActivity toRecentActivity(AuditLog log) {
        String description = log.getChangeDetails() != null ? log.getChangeDetails() : 
            (log.getAction() != null ? log.getAction().name() + " " + log.getResourceType() : "");
        
        return RecentActivity.builder()
            .id(log.getId())
            .action(log.getAction() != null ? log.getAction().name() : "UNKNOWN")
            .resourceType(log.getResourceType())
            .resourceId(log.getResourceId())
            .resourceName(log.getResourceName())
            .username(log.getUserName())
            .userId(log.getUserId())
            .description(description)
            .createdAt(log.getTimestamp() != null ? log.getTimestamp().toString() : null)
            .build();
    }
}
