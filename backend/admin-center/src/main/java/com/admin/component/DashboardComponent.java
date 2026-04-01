package com.admin.component;

import com.admin.dto.response.DashboardStats;
import com.admin.dto.response.RecentActivity;
import com.admin.dto.response.UserTrend;
import com.admin.entity.AuditLog;
import com.admin.repository.AuditLogRepository;
import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.RoleRepository;
import com.admin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class DashboardComponent {

    private final UserRepository userRepository;
    private final BusinessUnitRepository businessUnitRepository;
    private final RoleRepository roleRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public DashboardStats getStats() {
        long totalUsers = userRepository.count();
        long totalBusinessUnits = businessUnitRepository.count();
        long totalRoles = roleRepository.count();

        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);
        long onlineUsers = userRepository.countByLastLoginAtAfter(thirtyMinutesAgo);

        Instant todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        long todayLogins = auditLogRepository.countByActionAndTimestampAfter(
                com.admin.enums.AuditAction.USER_LOGIN, todayStart);

        LocalDateTime todayStartLocal = LocalDate.now().atStartOfDay();
        long todayNewUsers = userRepository.countByCreatedAtAfter(todayStartLocal);

        return DashboardStats.builder()
                .totalUsers(totalUsers)
                .totalBusinessUnits(totalBusinessUnits)
                .totalRoles(totalRoles)
                .onlineUsers(onlineUsers)
                .todayLogins(todayLogins)
                .todayNewUsers(todayNewUsers)
                .activeProcesses(0L)
                .pendingTasks(0L)
                .build();
    }

    @Transactional(readOnly = true)
    public List<RecentActivity> getRecentActivities(int limit) {
        List<AuditLog> logs = auditLogRepository.findAll(
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "timestamp"))).getContent();
        return logs.stream()
                .map(this::toRecentActivity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserTrend> getUserTrends(int days) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(days - 1);
        Instant rangeStart = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant rangeEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        LocalDateTime rangeStartLocal = startDate.atStartOfDay();
        LocalDateTime rangeEndLocal = today.plusDays(1).atStartOfDay();

        Map<LocalDate, long[]> loginStatsMap = new HashMap<>();
        List<Object[]> loginStats = auditLogRepository.countDailyLoginStats(
                com.admin.enums.AuditAction.USER_LOGIN, rangeStart, rangeEnd);
        for (Object[] row : loginStats) {
            LocalDate date = toLocalDate(row[0]);
            if (date != null) {
                long activeUsers = ((Number) row[1]).longValue();
                long loginCount = ((Number) row[2]).longValue();
                loginStatsMap.put(date, new long[]{activeUsers, loginCount});
            }
        }

        Map<LocalDate, Long> newUsersMap = new HashMap<>();
        List<Object[]> newUsersStats = userRepository.countDailyNewUsers(rangeStartLocal, rangeEndLocal);
        for (Object[] row : newUsersStats) {
            LocalDate date = toLocalDate(row[0]);
            if (date != null) {
                newUsersMap.put(date, ((Number) row[1]).longValue());
            }
        }

        List<UserTrend> trends = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            long[] loginData = loginStatsMap.getOrDefault(date, new long[]{0, 0});
            long newUsers = newUsersMap.getOrDefault(date, 0L);

            trends.add(UserTrend.builder()
                    .date(date.toString())
                    .activeUsers(loginData[0])
                    .newUsers(newUsers)
                    .loginCount(loginData[1])
                    .build());
        }

        return trends;
    }

    private LocalDate toLocalDate(Object dbDate) {
        if (dbDate instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        } else if (dbDate instanceof LocalDate ld) {
            return ld;
        } else if (dbDate instanceof java.util.Date utilDate) {
            return utilDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return null;
    }

    private RecentActivity toRecentActivity(AuditLog auditLog) {
        String description = auditLog.getChangeDetails() != null ? auditLog.getChangeDetails() :
                (auditLog.getAction() != null ? auditLog.getAction().name() + " " + auditLog.getResourceType() : "");

        return RecentActivity.builder()
                .id(auditLog.getId())
                .action(auditLog.getAction() != null ? auditLog.getAction().name() : "UNKNOWN")
                .resourceType(auditLog.getResourceType())
                .resourceId(auditLog.getResourceId())
                .resourceName(auditLog.getResourceName())
                .username(auditLog.getUserName())
                .userId(auditLog.getUserId())
                .description(description)
                .createdAt(auditLog.getTimestamp() != null ? auditLog.getTimestamp().toString() : null)
                .build();
    }
}
