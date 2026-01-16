package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.dto.DashboardOverview;
import com.portal.dto.ProcessInfo;
import com.portal.dto.TaskInfo;
import com.portal.dto.TaskQueryRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dashboard组件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardComponent {

    private final TaskQueryComponent taskQueryComponent;
    private final WorkflowEngineClient workflowEngineClient;

    /**
     * 获取Dashboard概览数据
     */
    public DashboardOverview getDashboardOverview(String userId) {
        // 获取任务概览
        DashboardOverview.TaskOverview taskOverview = getTaskOverview(userId);

        // 获取流程概览
        DashboardOverview.ProcessOverview processOverview = getProcessOverview(userId);

        // 获取个人绩效
        DashboardOverview.PerformanceOverview performanceOverview = getPerformanceOverview(userId);

        // 获取最近任务
        List<TaskInfo> recentTasks = getRecentTasks(userId, 5);

        // 获取最近流程
        List<ProcessInfo> recentProcesses = getRecentProcesses(userId, 5);

        return DashboardOverview.builder()
                .taskOverview(taskOverview)
                .processOverview(processOverview)
                .performanceOverview(performanceOverview)
                .recentTasks(recentTasks)
                .recentProcesses(recentProcesses)
                .build();
    }

    /**
     * 获取任务概览
     */
    public DashboardOverview.TaskOverview getTaskOverview(String userId) {
        TaskQueryRequest request = TaskQueryRequest.builder()
                .userId(userId)
                .build();
        
        var allTasks = taskQueryComponent.queryTasks(request).getContent();

        long pendingCount = allTasks.size();
        long overdueCount = allTasks.stream()
                .filter(t -> Boolean.TRUE.equals(t.getIsOverdue()))
                .count();
        long urgentCount = allTasks.stream()
                .filter(t -> "URGENT".equals(t.getPriority()) || "CRITICAL".equals(t.getPriority()))
                .count();
        long highPriorityCount = allTasks.stream()
                .filter(t -> "HIGH".equals(t.getPriority()))
                .count();

        // 从 Flowable 获取今日完成数
        long completedTodayCount = 0;
        try {
            Optional<Map<String, Object>> result = workflowEngineClient.getCompletedTasks(
                userId, 0, 1000, null, 
                LocalDate.now().atStartOfDay().toString(), 
                LocalDateTime.now().toString());
            if (result.isPresent()) {
                Object totalElements = result.get().get("totalElements");
                if (totalElements instanceof Number) {
                    completedTodayCount = ((Number) totalElements).longValue();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get completed tasks count: {}", e.getMessage());
        }
        
        // 平均处理时长（从已完成任务计算）
        double avgProcessingHours = 2.5; // 默认值
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
                    avgProcessingHours = totalDuration / (1000 * 60 * 60); // 转换为小时
                }
            }
        } catch (Exception e) {
            log.warn("Failed to calculate avg processing hours: {}", e.getMessage());
        }

        return DashboardOverview.TaskOverview.builder()
                .pendingCount(pendingCount)
                .overdueCount(overdueCount)
                .completedTodayCount(completedTodayCount)
                .avgProcessingHours(Math.round(avgProcessingHours * 10) / 10.0)
                .urgentCount(urgentCount)
                .highPriorityCount(highPriorityCount)
                .build();
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
