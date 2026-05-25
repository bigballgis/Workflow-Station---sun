package com.portal.component;

import com.platform.security.entity.BusinessUnit;
import com.platform.security.entity.UserBusinessUnit;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.DashboardOverview;
import com.portal.dto.PageResponse;
import com.portal.dto.ProcessInfo;
import com.portal.dto.TaskInfo;
import com.portal.dto.TaskQueryRequest;
import com.portal.dto.TeamRequestsResponse;
import com.portal.entity.ProcessInstance;
import com.portal.repository.BusinessUnitRepository;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.repository.UserBusinessUnitRepository;
import com.portal.service.UserDisplayNameResolver;
import com.platform.security.util.SecurityContextUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dashboard组件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardComponent {

    private final TaskQueryComponent taskQueryComponent;
    private final WorkflowEngineClient workflowEngineClient;
    private final BusinessUnitRepository businessUnitRepository;
    private final UserBusinessUnitRepository userBusinessUnitRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final UserDisplayNameResolver userDisplayNameResolver;

    /**
     * 为 true 时，首页「团队任务概览」按 BU 成员逐个调用 queryTasks（极慢，成员多时可打爆引擎）。
     * 默认 false：团队数字与本人待办一致（不聚合）；生产需要团队汇总时通过环境变量开启。
     */
    @Value("${portal.dashboard.team-task-aggregation-enabled:false}")
    private boolean teamTaskAggregationEnabled;

    @Value("${portal.dashboard.overview-cache-ttl-seconds:0}")
    private int overviewCacheTtlSeconds;

    @Value("${portal.dashboard.skip-avg-processing-hours:true}")
    private boolean skipAvgProcessingHoursForOverview;

    private final Map<String, OverviewCacheEntry> overviewCache = new ConcurrentHashMap<>();

    private static final class OverviewCacheEntry {
        final DashboardOverview overview;
        final long expiresAtMillis;

        OverviewCacheEntry(DashboardOverview overview, long expiresAtMillis) {
            this.overview = overview;
            this.expiresAtMillis = expiresAtMillis;
        }
    }

    private String overviewCacheKey(String userId) {
        String bu = SecurityContextUtils.getCurrentActiveBusinessUnitId().orElse("");
        return userId + "|" + bu;
    }

    /**
     * 获取Dashboard概览数据
     */
    public DashboardOverview getDashboardOverview(String userId) {
        if (overviewCacheTtlSeconds > 0) {
            String ck = overviewCacheKey(userId);
            OverviewCacheEntry hit = overviewCache.get(ck);
            if (hit != null && hit.expiresAtMillis > System.currentTimeMillis()) {
                return hit.overview;
            }
        }

        // 与 queryTasks 并行拉流程统计（均访问 workflow-engine，叠加快照可缩短首屏）
        CompletableFuture<DashboardOverview.ProcessOverview> processOverviewFuture =
                CompletableFuture.supplyAsync(() -> getProcessOverview(userId));

        TaskQueryRequest dashTaskRequest = TaskQueryRequest.builder()
                .userId(userId)
                .page(0)
                .size(1000)
                .sortBy("createTime")
                .sortDirection("desc")
                .build();
        PageResponse<TaskInfo> taskPage = taskQueryComponent.queryTasks(dashTaskRequest);

        // 与 buildTaskOverviewFromPage 内 history 调用重叠（均为本地 CPU 或轻量逻辑）
        CompletableFuture<DashboardOverview.PerformanceOverview> performanceFuture =
                CompletableFuture.supplyAsync(() -> getPerformanceOverview(userId));
        CompletableFuture<List<ProcessInfo>> recentProcessesFuture =
                CompletableFuture.supplyAsync(() -> getRecentProcesses(userId, 5));

        DashboardOverview.TaskOverview taskOverview = buildTaskOverviewFromPage(userId, taskPage);
        List<TaskInfo> recentTasks = taskPage.getContent().stream().limit(5).toList();

        CompletableFuture.allOf(processOverviewFuture, performanceFuture, recentProcessesFuture).join();
        DashboardOverview.ProcessOverview processOverview = processOverviewFuture.join();
        DashboardOverview.PerformanceOverview performanceOverview = performanceFuture.join();
        List<ProcessInfo> recentProcesses = recentProcessesFuture.join();

        DashboardOverview built = DashboardOverview.builder()
                .taskOverview(taskOverview)
                .processOverview(processOverview)
                .performanceOverview(performanceOverview)
                .recentTasks(recentTasks)
                .recentProcesses(recentProcesses)
                .build();

        if (overviewCacheTtlSeconds > 0) {
            overviewCache.put(
                    overviewCacheKey(userId),
                    new OverviewCacheEntry(built, System.currentTimeMillis() + overviewCacheTtlSeconds * 1000L));
        }
        return built;
    }

    /**
     * 获取任务概览
     */
    public DashboardOverview.TaskOverview getTaskOverview(String userId) {
        TaskQueryRequest request = TaskQueryRequest.builder()
                .userId(userId)
                .page(0)
                .size(1000)
                .build();
        PageResponse<TaskInfo> taskPage = taskQueryComponent.queryTasks(request);
        return buildTaskOverviewFromPage(userId, taskPage);
    }

    private DashboardOverview.TaskOverview buildTaskOverviewFromPage(String userId, PageResponse<TaskInfo> taskPage) {
        List<TaskInfo> allTasks = taskPage.getContent();
        long pendingCount = taskPage.getTotalElements();
        long overdueCount = 0;
        long urgentCount = 0;
        long highPriorityCount = 0;
        for (TaskInfo t : allTasks) {
            if (t == null) {
                continue;
            }
            if (Boolean.TRUE.equals(t.getIsOverdue())) {
                overdueCount++;
            }
            String p = t.getPriority();
            if (p != null) {
                if ("URGENT".equals(p) || "CRITICAL".equals(p)) {
                    urgentCount++;
                } else if ("HIGH".equals(p)) {
                    highPriorityCount++;
                }
            }
        }

        String completedRangeStart = LocalDate.now().atStartOfDay().toString();
        String completedRangeEnd = LocalDateTime.now().toString();

        long completedTodayCount;
        double avgProcessingHours;
        if (skipAvgProcessingHoursForOverview) {
            completedTodayCount = fetchCompletedTasksTotalInRange(
                    userId, completedRangeStart, completedRangeEnd);
            avgProcessingHours = 2.5;
        } else {
            CompletableFuture<Long> completedTodayFuture = CompletableFuture.supplyAsync(() ->
                    fetchCompletedTasksTotalInRange(userId, completedRangeStart, completedRangeEnd));
            CompletableFuture<Double> avgHoursFuture = CompletableFuture.supplyAsync(() ->
                    estimateAvgProcessingHoursFromRecentCompletions(userId));
            completedTodayCount = completedTodayFuture.join();
            avgProcessingHours = avgHoursFuture.join();
        }

        // Team aggregation logic
        long teamPendingCount = pendingCount;
        long teamOverdueCount = overdueCount;
        long teamCompletedTodayCount = completedTodayCount;

        final int MAX_TEAM_MEMBERS = 20;

        if (teamTaskAggregationEnabled) {
            try {
                Optional<String> activeBuOpt = SecurityContextUtils.getCurrentActiveBusinessUnitId();
            String activeBuId = activeBuOpt.orElse(null);
            if (activeBuId != null) {
                Set<String> allBuIds = new HashSet<>();
                allBuIds.add(activeBuId);
                collectChildBuIds(activeBuId, allBuIds);

                List<UserBusinessUnit> allMembers = userBusinessUnitRepository
                        .findByBusinessUnitIdIn(new ArrayList<>(allBuIds));
                List<String> teamMemberIds = allMembers.stream()
                        .map(UserBusinessUnit::getUserId)
                        .distinct()
                        .limit(MAX_TEAM_MEMBERS)
                        .toList();

                String todayStart = LocalDate.now().atStartOfDay().toString();
                String now = LocalDateTime.now().toString();

                List<CompletableFuture<long[]>> futures = teamMemberIds.stream()
                        .map(memberId -> CompletableFuture.supplyAsync(() -> {
                            long memberPending = 0;
                            long memberOverdue = 0;
                            long memberCompleted = 0;
                            try {
                                TaskQueryRequest memberRequest = TaskQueryRequest.builder()
                                        .userId(memberId)
                                        .page(0)
                                        .size(1000)
                                        .build();
                                PageResponse<TaskInfo> memberPage = taskQueryComponent.queryTasks(memberRequest);
                                memberPending = memberPage.getTotalElements();
                                memberOverdue = memberPage.getContent().stream()
                                        .filter(t -> Boolean.TRUE.equals(t.getIsOverdue()))
                                        .count();
                            } catch (Exception e) {
                                log.warn("Failed to get tasks for team member {}: {}", memberId, e.getMessage());
                            }
                            try {
                                Optional<Map<String, Object>> memberResult = workflowEngineClient.getCompletedTasks(
                                        memberId, 0, 1000, null, todayStart, now);
                                if (memberResult.isPresent()) {
                                    Object totalElements = memberResult.get().get("totalElements");
                                    if (totalElements instanceof Number) {
                                        memberCompleted = ((Number) totalElements).longValue();
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("Failed to get completed tasks for team member {}: {}", memberId, e.getMessage());
                            }
                            return new long[]{memberPending, memberOverdue, memberCompleted};
                        }))
                        .toList();

                long aggregatedPending = 0;
                long aggregatedOverdue = 0;
                long aggregatedCompletedToday = 0;
                for (CompletableFuture<long[]> f : futures) {
                    try {
                        long[] counts = f.join();
                        aggregatedPending += counts[0];
                        aggregatedOverdue += counts[1];
                        aggregatedCompletedToday += counts[2];
                    } catch (Exception e) {
                        log.warn("Failed to aggregate team member metrics: {}", e.getMessage());
                    }
                }

                teamPendingCount = aggregatedPending;
                teamOverdueCount = aggregatedOverdue;
                teamCompletedTodayCount = aggregatedCompletedToday;
            }
        } catch (Exception e) {
            log.warn("Failed to aggregate team metrics, falling back to personal values: {}", e.getMessage());
            teamPendingCount = pendingCount;
            teamOverdueCount = overdueCount;
            teamCompletedTodayCount = completedTodayCount;
        }
        }

        return DashboardOverview.TaskOverview.builder()
                .pendingCount(pendingCount)
                .overdueCount(overdueCount)
                .completedTodayCount(completedTodayCount)
                .avgProcessingHours(Math.round(avgProcessingHours * 10) / 10.0)
                .urgentCount(urgentCount)
                .highPriorityCount(highPriorityCount)
                .teamPendingCount(teamPendingCount)
                .teamOverdueCount(teamOverdueCount)
                .teamCompletedTodayCount(teamCompletedTodayCount)
                .build();
    }

    private long fetchCompletedTasksTotalInRange(String userId, String startIso, String endIso) {
        try {
            Optional<Map<String, Object>> result = workflowEngineClient.getCompletedTasks(
                    userId, 0, 1000, null, startIso, endIso);
            if (result.isPresent()) {
                Object totalElements = result.get().get("totalElements");
                if (totalElements instanceof Number) {
                    return ((Number) totalElements).longValue();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get completed tasks count: {}", e.getMessage());
        }
        return 0L;
    }

    /** 基于最近已完成任务样本估算平均处理时长（小时）；失败时返回 2.5 */
    private double estimateAvgProcessingHoursFromRecentCompletions(String userId) {
        try {
            Optional<Map<String, Object>> result = workflowEngineClient.getCompletedTasks(
                    userId, 0, 100, null, null, null);
            if (result.isPresent()) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> content = (List<Map<String, Object>>) result.get().get("content");
                if (content != null && !content.isEmpty()) {
                    double totalDuration = content.stream()
                            .filter(t -> t.get("durationInMillis") != null)
                            .mapToLong(t -> ((Number) t.get("durationInMillis")).longValue())
                            .average()
                            .orElse(0);
                    return totalDuration / (1000 * 60 * 60);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to calculate avg processing hours: {}", e.getMessage());
        }
        return 2.5;
    }

    /**
     * 递归收集所有子 BU ID
     */
    private void collectChildBuIds(String parentBuId, Set<String> allBuIds) {
        List<BusinessUnit> children = businessUnitRepository.findByParentIdAndStatus(parentBuId, "ACTIVE");
        for (BusinessUnit child : children) {
            if (allBuIds.add(child.getId())) {
                collectChildBuIds(child.getId(), allBuIds);
            }
        }
    }

    /**
     * 获取团队申请列表（当前用户 BU 及其子 BU 所有成员发起的流程实例）
     */
    public TeamRequestsResponse getTeamRequests(String userId, String status, int page, int size) {
        Set<String> teamMemberIds = resolveTeamMemberIds(userId);
        if (teamMemberIds.isEmpty()) {
            return TeamRequestsResponse.builder()
                    .content(List.of())
                    .build();
        }

        long overallCount = processInstanceRepository.countByStartUserIdIn(teamMemberIds);
        long runningCount = processInstanceRepository.countByStartUserIdInAndStatus(teamMemberIds, "RUNNING");
        long completedCount = processInstanceRepository.countByStartUserIdInAndStatus(teamMemberIds, "COMPLETED");
        long withdrawnCount = processInstanceRepository.countByStartUserIdInAndStatus(teamMemberIds, "WITHDRAWN");

        Pageable pageable = PageRequest.of(page, size);
        Page<ProcessInstance> resultPage;
        if (status == null || status.isBlank()) {
            resultPage = processInstanceRepository.findByStartUserIdInOrderByStartTimeDesc(teamMemberIds, pageable);
        } else {
            resultPage = processInstanceRepository.findByStartUserIdInAndStatusOrderByStartTimeDesc(teamMemberIds, status, pageable);
        }

        List<ProcessInstance> pageContent = resultPage.getContent();
        Set<String> assigneeKeys = pageContent.stream()
                .map(ProcessInstance::getCurrentAssignee)
                .filter(a -> a != null && !a.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<String, String> assigneeNames = userDisplayNameResolver.resolveBatch(assigneeKeys);

        List<TeamRequestsResponse.TeamRequestItem> items = pageContent.stream()
                .map(pi -> {
                    String rawAssignee = pi.getCurrentAssignee();
                    String displayAssignee = rawAssignee != null && !rawAssignee.isBlank()
                            ? assigneeNames.getOrDefault(rawAssignee.trim(), rawAssignee.trim())
                            : null;
                    return TeamRequestsResponse.TeamRequestItem.builder()
                        .id(pi.getId())
                        .processDefinitionName(pi.getProcessDefinitionName())
                        .businessKey(pi.getBusinessKey())
                        .startUserName(pi.getStartUserName())
                        .status(pi.getStatus())
                        .currentNode("COMPLETED".equals(pi.getStatus()) ? null : pi.getCurrentNode())
                        .currentAssignee(displayAssignee)
                        .startTime(pi.getStartTime())
                        .completedAt(pi.getCompletedAt())
                        .build();
                })
                .toList();

        return TeamRequestsResponse.builder()
                .overallCount(overallCount)
                .runningCount(runningCount)
                .completedCount(completedCount)
                .withdrawnCount(withdrawnCount)
                .content(items)
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .build();
    }

    /**
     * 解析当前活动 BU 及其子 BU 的全部成员 ID（仅限 JWT 中的 activeBusinessUnitId）
     */
    private Set<String> resolveTeamMemberIds(String userId) {
        Optional<String> activeBuOpt = SecurityContextUtils.getCurrentActiveBusinessUnitId();
        if (activeBuOpt.isEmpty()) {
            return Set.of();
        }
        String activeBuId = activeBuOpt.get();
        Set<String> allBuIds = new HashSet<>();
        allBuIds.add(activeBuId);
        collectChildBuIds(activeBuId, allBuIds);
        List<UserBusinessUnit> allMembers = userBusinessUnitRepository
                .findByBusinessUnitIdIn(new ArrayList<>(allBuIds));
        Set<String> memberIds = new HashSet<>();
        for (UserBusinessUnit m : allMembers) {
            memberIds.add(m.getUserId());
        }
        return memberIds;
    }

    /**
     * 获取流程概览
     */
    @SuppressWarnings("unchecked")
    public DashboardOverview.ProcessOverview getProcessOverview(String userId) {
        // 从 workflow-engine-core 获取真实数据
        try {
            Optional<Map<String, Object>> result = workflowEngineClient.getProcessStatistics(userId);
            if (result.isPresent()) {
                Map<String, Object> data = result.get();
                
                long initiatedCount = data.get("initiatedCount") != null 
                    ? ((Number) data.get("initiatedCount")).longValue() : 0;
                long inProgressCount = data.get("inProgressCount") != null 
                    ? ((Number) data.get("inProgressCount")).longValue() : 0;
                long completedThisMonthCount = data.get("completedThisMonthCount") != null 
                    ? ((Number) data.get("completedThisMonthCount")).longValue() : 0;
                double approvalRate = data.get("approvalRate") != null 
                    ? ((Number) data.get("approvalRate")).doubleValue() : 1.0;
                
                Map<String, Long> typeDistribution = new HashMap<>();
                Object typeDistObj = data.get("typeDistribution");
                if (typeDistObj instanceof Map) {
                    Map<String, Object> typeDist = (Map<String, Object>) typeDistObj;
                    for (Map.Entry<String, Object> entry : typeDist.entrySet()) {
                        if (entry.getValue() instanceof Number) {
                            typeDistribution.put(entry.getKey(), ((Number) entry.getValue()).longValue());
                        }
                    }
                }
                
                return DashboardOverview.ProcessOverview.builder()
                        .initiatedCount(initiatedCount)
                        .inProgressCount(inProgressCount)
                        .completedThisMonthCount(completedThisMonthCount)
                        .approvalRate(approvalRate)
                        .typeDistribution(typeDistribution)
                        .build();
            }
        } catch (Exception e) {
            log.warn("Failed to get process statistics from workflow engine: {}", e.getMessage());
        }
        
        // 如果获取失败，返回空数据
        return DashboardOverview.ProcessOverview.builder()
                .initiatedCount(0L)
                .inProgressCount(0L)
                .completedThisMonthCount(0L)
                .approvalRate(1.0)
                .typeDistribution(new HashMap<>())
                .build();
    }

    /**
     * 获取个人绩效
     */
    public DashboardOverview.PerformanceOverview getPerformanceOverview(String userId) {
        // 模拟数据，实际应从统计服务获取
        Random random = new Random();

        return DashboardOverview.PerformanceOverview.builder()
                .efficiencyScore(80 + random.nextDouble() * 15)
                .qualityScore(85 + random.nextDouble() * 10)
                .collaborationScore(75 + random.nextDouble() * 20)
                .monthlyRank(1 + random.nextInt(50))
                .totalUsers(100)
                .build();
    }

    /**
     * 获取最近任务
     */
    public List<TaskInfo> getRecentTasks(String userId, int limit) {
        TaskQueryRequest request = TaskQueryRequest.builder()
                .userId(userId)
                .sortBy("createTime")
                .sortDirection("desc")
                .size(limit)
                .build();
        
        return taskQueryComponent.queryTasks(request).getContent();
    }

    /**
     * 获取最近流程
     */
    public List<ProcessInfo> getRecentProcesses(String userId, int limit) {
        // 模拟数据，实际应从workflow-engine-core获取
        List<ProcessInfo> processes = new ArrayList<>();
        
        String[] processNames = {"请假申请", "报销申请", "采购申请", "出差申请", "加班申请"};
        String[] statuses = {"RUNNING", "COMPLETED", "RUNNING", "COMPLETED", "RUNNING"};
        
        for (int i = 0; i < Math.min(limit, processNames.length); i++) {
            processes.add(ProcessInfo.builder()
                    .processInstanceId("PI_" + UUID.randomUUID().toString().substring(0, 8))
                    .processDefinitionKey("process_" + (i + 1))
                    .processDefinitionName(processNames[i])
                    .status(statuses[i])
                    .initiatorId(userId)
                    .initiatorName("当前用户")
                    .startTime(LocalDateTime.now().minusDays(i))
                    .build());
        }
        
        return processes;
    }

    /**
     * 获取任务趋势数据
     */
    public Map<String, Object> getTaskTrendData(String userId, int days) {
        Map<String, Object> result = new HashMap<>();
        
        List<String> dates = new ArrayList<>();
        List<Long> completedCounts = new ArrayList<>();
        List<Long> receivedCounts = new ArrayList<>();
        
        Random random = new Random();
        LocalDate today = LocalDate.now();
        
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            dates.add(date.toString());
            completedCounts.add((long) (5 + random.nextInt(10)));
            receivedCounts.add((long) (3 + random.nextInt(12)));
        }
        
        result.put("dates", dates);
        result.put("completed", completedCounts);
        result.put("received", receivedCounts);
        
        return result;
    }

    /**
     * 获取流程统计数据
     */
    public Map<String, Object> getProcessStatisticsData(String userId) {
        Map<String, Object> result = new HashMap<>();
        
        // 流程类型分布
        Map<String, Long> typeDistribution = new LinkedHashMap<>();
        typeDistribution.put("请假申请", 25L);
        typeDistribution.put("报销申请", 18L);
        typeDistribution.put("采购申请", 12L);
        typeDistribution.put("出差申请", 8L);
        typeDistribution.put("其他", 5L);
        
        // 月度统计
        List<Map<String, Object>> monthlyStats = new ArrayList<>();
        String[] months = {"1月", "2月", "3月", "4月", "5月", "6月"};
        Random random = new Random();
        for (String month : months) {
            Map<String, Object> stat = new HashMap<>();
            stat.put("month", month);
            stat.put("initiated", 10 + random.nextInt(20));
            stat.put("completed", 8 + random.nextInt(18));
            monthlyStats.add(stat);
        }
        
        result.put("typeDistribution", typeDistribution);
        result.put("monthlyStats", monthlyStats);
        
        return result;
    }
}
