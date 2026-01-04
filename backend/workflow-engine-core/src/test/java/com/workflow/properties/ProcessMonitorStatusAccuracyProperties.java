package com.workflow.properties;

import com.workflow.dto.request.ProcessMonitorQueryRequest;
import com.workflow.dto.response.ProcessMonitorResult;
import com.workflow.dto.response.ProcessStatisticsResult;
import com.workflow.dto.response.TaskStatisticsResult;
import com.workflow.dto.response.PerformanceMetricsResult;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * 流程监控状态准确性属性测试
 * 验证需求: 需求 5.1 - 流程监控状态查询
 * 
 * 属性 11: 流程监控状态准确性
 * 对于任何流程实例状态查询，返回的监控信息应该准确反映实际的流程状态。
 * 统计数据应该与实际的流程实例数量一致，状态分类应该正确无误。
 * 
 * 注意：这是一个简化的属性测试，主要验证监控状态准确性逻辑的正确性，不依赖Spring Boot上下文
 */
@Label("功能: workflow-engine-core, 属性 11: 流程监控状态准确性")
public class ProcessMonitorStatusAccuracyProperties {

    // 模拟流程实例存储，使用内存Map来测试核心逻辑
    private final Map<String, ProcessInstanceStorage> processInstances = new ConcurrentHashMap<>();
    private final Map<String, TaskStorage> tasks = new ConcurrentHashMap<>();
    
    /**
     * 简化的流程实例存储类
     */
    private static class ProcessInstanceStorage {
        private final String id;
        private final String processDefinitionKey;
        private final String businessKey;
        private final String status; // ACTIVE, COMPLETED, TERMINATED
        private final Date startTime;
        private final Date endTime;
        private final String startUserId;
        private final Long durationInMillis;
        private final boolean suspended;
        
        public ProcessInstanceStorage(String id, String processDefinitionKey, String businessKey, 
                                    String status, Date startTime, Date endTime, String startUserId, 
                                    Long durationInMillis, boolean suspended) {
            this.id = id;
            this.processDefinitionKey = processDefinitionKey;
            this.businessKey = businessKey;
            this.status = status;
            this.startTime = startTime;
            this.endTime = endTime;
            this.startUserId = startUserId;
            this.durationInMillis = durationInMillis;
            this.suspended = suspended;
        }
        
        // Getters
        public String getId() { return id; }
        public String getProcessDefinitionKey() { return processDefinitionKey; }
        public String getBusinessKey() { return businessKey; }
        public String getStatus() { return status; }
        public Date getStartTime() { return startTime; }
        public Date getEndTime() { return endTime; }
        public String getStartUserId() { return startUserId; }
        public Long getDurationInMillis() { return durationInMillis; }
        public boolean isSuspended() { return suspended; }
    }
    
    /**
     * 简化的任务存储类
     */
    private static class TaskStorage {
        private final String id;
        private final String name;
        private final String assignee;
        private final String processInstanceId;
        private final String processDefinitionKey;
        private final String status; // PENDING, COMPLETED, OVERDUE
        private final Date createTime;
        private final Date completeTime;
        private final Date dueDate;
        private final Long durationInMillis;
        
        public TaskStorage(String id, String name, String assignee, String processInstanceId, 
                         String processDefinitionKey, String status, Date createTime, 
                         Date completeTime, Date dueDate, Long durationInMillis) {
            this.id = id;
            this.name = name;
            this.assignee = assignee;
            this.processInstanceId = processInstanceId;
            this.processDefinitionKey = processDefinitionKey;
            this.status = status;
            this.createTime = createTime;
            this.completeTime = completeTime;
            this.dueDate = dueDate;
            this.durationInMillis = durationInMillis;
        }
        
        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public String getAssignee() { return assignee; }
        public String getProcessInstanceId() { return processInstanceId; }
        public String getProcessDefinitionKey() { return processDefinitionKey; }
        public String getStatus() { return status; }
        public Date getCreateTime() { return createTime; }
        public Date getCompleteTime() { return completeTime; }
        public Date getDueDate() { return dueDate; }
        public Long getDurationInMillis() { return durationInMillis; }
    }

    /**
     * 属性测试: 流程实例状态查询准确性
     */
    @Property(tries = 100)
    @Label("流程实例状态查询准确性")
    void processInstanceStatusQueryAccuracy(@ForAll @NotBlank @Size(min = 1, max = 50) String processDefinitionKey,
                                          @ForAll @Size(min = 1, max = 3) List<@NotBlank @Size(min = 1, max = 20) String> businessKeys) {
        Assume.that(!businessKeys.isEmpty());
        Assume.that(businessKeys.stream().distinct().count() == businessKeys.size()); // 确保业务键唯一
        
        // Given: 创建不同状态的流程实例
        List<String> instanceIds = new ArrayList<>();
        Date now = new Date();
        
        for (int i = 0; i < businessKeys.size(); i++) {
            String businessKey = businessKeys.get(i);
            String instanceId = "proc-inst-" + UUID.randomUUID().toString().substring(0, 8);
            String status = i % 3 == 0 ? "ACTIVE" : (i % 3 == 1 ? "COMPLETED" : "TERMINATED");
            
            Date startTime = new Date(now.getTime() - (i + 1) * 3600000); // 每个实例间隔1小时
            Date endTime = "ACTIVE".equals(status) ? null : new Date(startTime.getTime() + 1800000); // 30分钟后结束
            Long duration = endTime != null ? (endTime.getTime() - startTime.getTime()) : null;
            
            ProcessInstanceStorage instance = new ProcessInstanceStorage(
                    instanceId, processDefinitionKey, businessKey, status, 
                    startTime, endTime, "user" + i, duration, false
            );
            
            processInstances.put(instanceId, instance);
            instanceIds.add(instanceId);
        }
        
        // When: 查询所有流程实例
        ProcessMonitorQueryRequest request = ProcessMonitorQueryRequest.builder()
                .processDefinitionKey(processDefinitionKey)
                .limit(20)
                .offset(0)
                .build();
        
        ProcessMonitorResult result = queryProcessInstances(request);
        
        // Then: 查询结果应该准确反映实际状态
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProcessInstances()).hasSize(businessKeys.size());
        assertThat(result.getTotalCount()).isEqualTo(businessKeys.size());
        
        // 验证每个实例的状态准确性
        Map<String, String> expectedStatuses = processInstances.values().stream()
                .filter(inst -> processDefinitionKey.equals(inst.getProcessDefinitionKey()))
                .collect(Collectors.toMap(
                        ProcessInstanceStorage::getId,
                        ProcessInstanceStorage::getStatus
                ));
        
        for (Map<String, Object> instanceInfo : result.getProcessInstances()) {
            String instanceId = (String) instanceInfo.get("id");
            String actualStatus = (String) instanceInfo.get("status");
            String expectedStatus = expectedStatuses.get(instanceId);
            
            assertThat(actualStatus).isEqualTo(expectedStatus);
            assertThat(instanceInfo.get("processDefinitionKey")).isEqualTo(processDefinitionKey);
        }
    }

    /**
     * 属性测试: 流程统计数据准确性
     */
    @Property(tries = 100)
    @Label("流程统计数据准确性")
    void processStatisticsAccuracy(@ForAll @NotBlank @Size(min = 1, max = 50) String processDefinitionKey) {
        // Given: 创建已知数量的不同状态流程实例
        int activeCount = 3;
        int completedCount = 5;
        int terminatedCount = 2;
        
        Date now = new Date();
        List<String> instanceIds = new ArrayList<>();
        
        // 创建活跃实例
        for (int i = 0; i < activeCount; i++) {
            String instanceId = "active-" + UUID.randomUUID().toString().substring(0, 8);
            ProcessInstanceStorage instance = new ProcessInstanceStorage(
                    instanceId, processDefinitionKey, "business-" + i, "ACTIVE",
                    new Date(now.getTime() - i * 3600000), null, "user" + i, null, false
            );
            processInstances.put(instanceId, instance);
            instanceIds.add(instanceId);
        }
        
        // 创建已完成实例
        for (int i = 0; i < completedCount; i++) {
            String instanceId = "completed-" + UUID.randomUUID().toString().substring(0, 8);
            Date startTime = new Date(now.getTime() - (i + 10) * 3600000);
            Date endTime = new Date(startTime.getTime() + 1800000);
            ProcessInstanceStorage instance = new ProcessInstanceStorage(
                    instanceId, processDefinitionKey, "business-comp-" + i, "COMPLETED",
                    startTime, endTime, "user" + i, endTime.getTime() - startTime.getTime(), false
            );
            processInstances.put(instanceId, instance);
            instanceIds.add(instanceId);
        }
        
        // 创建已终止实例
        for (int i = 0; i < terminatedCount; i++) {
            String instanceId = "terminated-" + UUID.randomUUID().toString().substring(0, 8);
            Date startTime = new Date(now.getTime() - (i + 20) * 3600000);
            Date endTime = new Date(startTime.getTime() + 900000);
            ProcessInstanceStorage instance = new ProcessInstanceStorage(
                    instanceId, processDefinitionKey, "business-term-" + i, "TERMINATED",
                    startTime, endTime, "user" + i, endTime.getTime() - startTime.getTime(), false
            );
            processInstances.put(instanceId, instance);
            instanceIds.add(instanceId);
        }
        
        // When: 获取流程统计信息
        ProcessStatisticsResult statistics = getProcessStatistics(processDefinitionKey, null, null);
        
        // Then: 统计数据应该准确
        assertThat(statistics.getActiveCount()).isEqualTo(activeCount);
        assertThat(statistics.getCompletedCount()).isEqualTo(completedCount);
        assertThat(statistics.getTerminatedCount()).isEqualTo(terminatedCount);
        assertThat(statistics.getTotalCount()).isEqualTo(activeCount + completedCount + terminatedCount);
        
        // 验证状态统计
        Map<String, Long> statusStats = statistics.getStatusStatistics();
        assertThat(statusStats.get("ACTIVE")).isEqualTo(activeCount);
        assertThat(statusStats.get("COMPLETED")).isEqualTo(completedCount);
        assertThat(statusStats.get("TERMINATED")).isEqualTo(terminatedCount);
        
        // 验证流程定义统计
        Map<String, Long> processDefStats = statistics.getProcessDefinitionStatistics();
        assertThat(processDefStats.get(processDefinitionKey)).isEqualTo(activeCount + completedCount + terminatedCount);
    }

    /**
     * 属性测试: 任务统计数据准确性
     */
    @Property(tries = 100)
    @Label("任务统计数据准确性")
    void taskStatisticsAccuracy(@ForAll @NotBlank @Size(min = 1, max = 20) String assignee,
                              @ForAll @NotBlank @Size(min = 1, max = 50) String processDefinitionKey) {
        // Given: 创建已知数量的不同状态任务
        int pendingCount = 4;
        int completedCount = 6;
        int overdueCount = 2;
        
        Date now = new Date();
        List<String> taskIds = new ArrayList<>();
        
        // 创建待办任务
        for (int i = 0; i < pendingCount; i++) {
            String taskId = "pending-" + UUID.randomUUID().toString().substring(0, 8);
            TaskStorage task = new TaskStorage(
                    taskId, "Task " + i, assignee, "proc-" + i, processDefinitionKey,
                    "PENDING", new Date(now.getTime() - i * 3600000), null, 
                    new Date(now.getTime() + 86400000), null // 明天到期
            );
            tasks.put(taskId, task);
            taskIds.add(taskId);
        }
        
        // 创建已完成任务
        for (int i = 0; i < completedCount; i++) {
            String taskId = "completed-" + UUID.randomUUID().toString().substring(0, 8);
            Date createTime = new Date(now.getTime() - (i + 10) * 3600000);
            Date completeTime = new Date(createTime.getTime() + 1800000);
            TaskStorage task = new TaskStorage(
                    taskId, "Completed Task " + i, assignee, "proc-comp-" + i, processDefinitionKey,
                    "COMPLETED", createTime, completeTime, null, 
                    completeTime.getTime() - createTime.getTime()
            );
            tasks.put(taskId, task);
            taskIds.add(taskId);
        }
        
        // 创建超时任务
        for (int i = 0; i < overdueCount; i++) {
            String taskId = "overdue-" + UUID.randomUUID().toString().substring(0, 8);
            TaskStorage task = new TaskStorage(
                    taskId, "Overdue Task " + i, assignee, "proc-over-" + i, processDefinitionKey,
                    "OVERDUE", new Date(now.getTime() - (i + 20) * 3600000), null,
                    new Date(now.getTime() - 3600000), null // 1小时前到期
            );
            tasks.put(taskId, task);
            taskIds.add(taskId);
        }
        
        // When: 获取任务统计信息
        TaskStatisticsResult statistics = getTaskStatistics(assignee, processDefinitionKey, null, null);
        
        // Then: 统计数据应该准确
        assertThat(statistics.getPendingCount()).isEqualTo(pendingCount);
        assertThat(statistics.getCompletedCount()).isEqualTo(completedCount);
        assertThat(statistics.getOverdueCount()).isEqualTo(overdueCount);
        assertThat(statistics.getTotalCount()).isEqualTo(pendingCount + completedCount);
        
        // 验证任务名称统计
        Map<String, Long> taskNameStats = statistics.getTaskNameStatistics();
        assertThat(taskNameStats.values().stream().mapToLong(Long::longValue).sum())
                .isEqualTo(completedCount); // 只统计已完成任务的名称
        
        // 验证分配人统计
        Map<String, Long> assigneeStats = statistics.getAssigneeStatistics();
        assertThat(assigneeStats.get(assignee)).isEqualTo(completedCount);
        
        // 验证平均处理时间
        assertThat(statistics.getAverageProcessingTime()).isNotNull();
        assertThat(statistics.getAverageProcessingTime()).isGreaterThanOrEqualTo(0.0);
    }

    /**
     * 属性测试: 性能指标计算准确性
     */
    @Property(tries = 100)
    @Label("性能指标计算准确性")
    void performanceMetricsAccuracy(@ForAll @NotBlank @Size(min = 1, max = 50) String processDefinitionKey) {
        // Given: 创建已知执行时间的流程实例
        Date now = new Date();
        List<Long> durations = Arrays.asList(1800000L, 3600000L, 2700000L, 5400000L); // 30分钟, 1小时, 45分钟, 1.5小时
        
        for (int i = 0; i < durations.size(); i++) {
            String instanceId = "perf-" + UUID.randomUUID().toString().substring(0, 8);
            Date startTime = new Date(now.getTime() - (i + 1) * 7200000); // 每个实例间隔2小时
            Date endTime = new Date(startTime.getTime() + durations.get(i));
            
            ProcessInstanceStorage instance = new ProcessInstanceStorage(
                    instanceId, processDefinitionKey, "business-perf-" + i, "COMPLETED",
                    startTime, endTime, "user" + i, durations.get(i), false
            );
            processInstances.put(instanceId, instance);
        }
        
        // When: 获取性能指标
        PerformanceMetricsResult metrics = getPerformanceMetrics(processDefinitionKey, null, null);
        
        // Then: 性能指标应该准确计算
        assertThat(metrics.getAverageProcessDuration()).isNotNull();
        assertThat(metrics.getMaxProcessDuration()).isNotNull();
        assertThat(metrics.getMinProcessDuration()).isNotNull();
        assertThat(metrics.getProcessSuccessRate()).isNotNull();
        
        // 验证平均执行时间
        double expectedAvgDuration = durations.stream().mapToLong(Long::longValue).average().orElse(0.0) / 1000.0;
        assertThat(metrics.getAverageProcessDuration()).isCloseTo(expectedAvgDuration, within(1.0));
        
        // 验证最大执行时间
        long expectedMaxDuration = durations.stream().mapToLong(Long::longValue).max().orElse(0L) / 1000;
        assertThat(metrics.getMaxProcessDuration()).isEqualTo(expectedMaxDuration);
        
        // 验证最小执行时间
        long expectedMinDuration = durations.stream().mapToLong(Long::longValue).min().orElse(0L) / 1000;
        assertThat(metrics.getMinProcessDuration()).isEqualTo(expectedMinDuration);
        
        // 验证成功率（所有实例都是COMPLETED状态）
        assertThat(metrics.getProcessSuccessRate()).isEqualTo(100.0);
        
        // 验证资源利用率
        assertThat(metrics.getResourceUtilization()).isNotNull();
        assertThat(metrics.getResourceUtilization()).containsKeys("cpu", "memory", "disk", "network");
    }

    /**
     * 属性测试: 状态过滤查询准确性
     */
    @Property(tries = 100)
    @Label("状态过滤查询准确性")
    void statusFilterQueryAccuracy(@ForAll @NotBlank @Size(min = 1, max = 50) String processDefinitionKey) {
        // Given: 创建不同状态的流程实例
        Date now = new Date();
        List<String> activeIds = new ArrayList<>();
        List<String> completedIds = new ArrayList<>();
        List<String> terminatedIds = new ArrayList<>();
        
        // 创建各种状态的实例
        for (int i = 0; i < 3; i++) {
            // 活跃实例
            String activeId = "active-filter-" + UUID.randomUUID().toString().substring(0, 8);
            ProcessInstanceStorage activeInstance = new ProcessInstanceStorage(
                    activeId, processDefinitionKey, "business-active-" + i, "ACTIVE",
                    new Date(now.getTime() - i * 3600000), null, "user" + i, null, false
            );
            processInstances.put(activeId, activeInstance);
            activeIds.add(activeId);
            
            // 已完成实例
            String completedId = "completed-filter-" + UUID.randomUUID().toString().substring(0, 8);
            Date startTime = new Date(now.getTime() - (i + 10) * 3600000);
            Date endTime = new Date(startTime.getTime() + 1800000);
            ProcessInstanceStorage completedInstance = new ProcessInstanceStorage(
                    completedId, processDefinitionKey, "business-completed-" + i, "COMPLETED",
                    startTime, endTime, "user" + i, endTime.getTime() - startTime.getTime(), false
            );
            processInstances.put(completedId, completedInstance);
            completedIds.add(completedId);
            
            // 已终止实例
            String terminatedId = "terminated-filter-" + UUID.randomUUID().toString().substring(0, 8);
            Date termStartTime = new Date(now.getTime() - (i + 20) * 3600000);
            Date termEndTime = new Date(termStartTime.getTime() + 900000);
            ProcessInstanceStorage terminatedInstance = new ProcessInstanceStorage(
                    terminatedId, processDefinitionKey, "business-terminated-" + i, "TERMINATED",
                    termStartTime, termEndTime, "user" + i, termEndTime.getTime() - termStartTime.getTime(), false
            );
            processInstances.put(terminatedId, terminatedInstance);
            terminatedIds.add(terminatedId);
        }
        
        // When & Then: 测试不同状态过滤
        
        // 查询活跃实例
        ProcessMonitorQueryRequest activeRequest = ProcessMonitorQueryRequest.builder()
                .processDefinitionKey(processDefinitionKey)
                .status("ACTIVE")
                .limit(20)
                .offset(0)
                .build();
        
        ProcessMonitorResult activeResult = queryProcessInstances(activeRequest);
        assertThat(activeResult.isSuccess()).isTrue();
        assertThat(activeResult.getProcessInstances()).hasSize(3);
        
        for (Map<String, Object> instance : activeResult.getProcessInstances()) {
            assertThat(instance.get("status")).isEqualTo("ACTIVE");
            assertThat(activeIds).contains((String) instance.get("id"));
        }
        
        // 查询已完成实例
        ProcessMonitorQueryRequest completedRequest = ProcessMonitorQueryRequest.builder()
                .processDefinitionKey(processDefinitionKey)
                .status("COMPLETED")
                .limit(20)
                .offset(0)
                .build();
        
        ProcessMonitorResult completedResult = queryProcessInstances(completedRequest);
        assertThat(completedResult.isSuccess()).isTrue();
        assertThat(completedResult.getProcessInstances()).hasSize(3);
        
        for (Map<String, Object> instance : completedResult.getProcessInstances()) {
            assertThat(instance.get("status")).isEqualTo("COMPLETED");
            assertThat(completedIds).contains((String) instance.get("id"));
        }
        
        // 查询已终止实例
        ProcessMonitorQueryRequest terminatedRequest = ProcessMonitorQueryRequest.builder()
                .processDefinitionKey(processDefinitionKey)
                .status("TERMINATED")
                .limit(20)
                .offset(0)
                .build();
        
        ProcessMonitorResult terminatedResult = queryProcessInstances(terminatedRequest);
        assertThat(terminatedResult.isSuccess()).isTrue();
        assertThat(terminatedResult.getProcessInstances()).hasSize(3);
        
        for (Map<String, Object> instance : terminatedResult.getProcessInstances()) {
            assertThat(instance.get("status")).isEqualTo("TERMINATED");
            assertThat(terminatedIds).contains((String) instance.get("id"));
        }
    }

    /**
     * 属性测试: 时间范围查询准确性
     */
    @Property(tries = 100)
    @Label("时间范围查询准确性")
    void timeRangeQueryAccuracy(@ForAll @NotBlank @Size(min = 1, max = 50) String processDefinitionKey) {
        // Given: 创建不同时间的流程实例
        Date now = new Date();
        Date startRange = new Date(now.getTime() - 7200000); // 2小时前
        Date endRange = new Date(now.getTime() - 3600000);   // 1小时前
        
        List<String> inRangeIds = new ArrayList<>();
        List<String> outRangeIds = new ArrayList<>();
        
        // 在时间范围内的实例
        for (int i = 0; i < 3; i++) {
            String instanceId = "in-range-" + UUID.randomUUID().toString().substring(0, 8);
            Date startTime = new Date(startRange.getTime() + i * 600000); // 范围内，间隔10分钟
            ProcessInstanceStorage instance = new ProcessInstanceStorage(
                    instanceId, processDefinitionKey, "business-in-" + i, "COMPLETED",
                    startTime, new Date(startTime.getTime() + 1800000), "user" + i, 1800000L, false
            );
            processInstances.put(instanceId, instance);
            inRangeIds.add(instanceId);
        }
        
        // 在时间范围外的实例
        for (int i = 0; i < 2; i++) {
            String instanceId = "out-range-" + UUID.randomUUID().toString().substring(0, 8);
            Date startTime = new Date(now.getTime() - 10800000 - i * 3600000); // 3小时前开始，范围外
            ProcessInstanceStorage instance = new ProcessInstanceStorage(
                    instanceId, processDefinitionKey, "business-out-" + i, "COMPLETED",
                    startTime, new Date(startTime.getTime() + 1800000), "user" + i, 1800000L, false
            );
            processInstances.put(instanceId, instance);
            outRangeIds.add(instanceId);
        }
        
        // When: 按时间范围查询
        ProcessMonitorQueryRequest request = ProcessMonitorQueryRequest.builder()
                .processDefinitionKey(processDefinitionKey)
                .startTime(startRange)
                .endTime(endRange)
                .limit(20)
                .offset(0)
                .build();
        
        ProcessMonitorResult result = queryProcessInstances(request);
        
        // Then: 只应该返回时间范围内的实例
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProcessInstances()).hasSize(3);
        
        for (Map<String, Object> instance : result.getProcessInstances()) {
            String instanceId = (String) instance.get("id");
            assertThat(inRangeIds).contains(instanceId);
            assertThat(outRangeIds).doesNotContain(instanceId);
            
            Date instanceStartTime = (Date) instance.get("startTime");
            assertThat(instanceStartTime).isBetween(startRange, endRange, true, true);
        }
    }

    /**
     * 属性测试: 分页查询准确性
     */
    @Property(tries = 50)
    @Label("分页查询准确性")
    void paginationQueryAccuracy(@ForAll @NotBlank @Size(min = 1, max = 50) String processDefinitionKey) {
        // Given: 创建足够多的流程实例用于分页测试
        int totalInstances = 7;
        List<String> allInstanceIds = new ArrayList<>();
        Date now = new Date();
        
        for (int i = 0; i < totalInstances; i++) {
            String instanceId = "page-test-" + UUID.randomUUID().toString().substring(0, 8);
            ProcessInstanceStorage instance = new ProcessInstanceStorage(
                    instanceId, processDefinitionKey, "business-page-" + i, "ACTIVE",
                    new Date(now.getTime() - i * 3600000), null, "user" + i, null, false
            );
            processInstances.put(instanceId, instance);
            allInstanceIds.add(instanceId);
        }
        
        // When & Then: 测试分页查询
        int pageSize = 3;
        List<String> allPaginatedIds = new ArrayList<>();
        
        // 第一页
        ProcessMonitorQueryRequest page1Request = ProcessMonitorQueryRequest.builder()
                .processDefinitionKey(processDefinitionKey)
                .limit(pageSize)
                .offset(0)
                .build();
        
        ProcessMonitorResult page1Result = queryProcessInstances(page1Request);
        assertThat(page1Result.isSuccess()).isTrue();
        assertThat(page1Result.getProcessInstances()).hasSize(pageSize);
        assertThat(page1Result.getCurrentPage()).isEqualTo(1);
        assertThat(page1Result.getPageSize()).isEqualTo(pageSize);
        
        page1Result.getProcessInstances().forEach(inst -> 
                allPaginatedIds.add((String) inst.get("id")));
        
        // 第二页
        ProcessMonitorQueryRequest page2Request = ProcessMonitorQueryRequest.builder()
                .processDefinitionKey(processDefinitionKey)
                .limit(pageSize)
                .offset(pageSize)
                .build();
        
        ProcessMonitorResult page2Result = queryProcessInstances(page2Request);
        assertThat(page2Result.isSuccess()).isTrue();
        assertThat(page2Result.getProcessInstances()).hasSize(pageSize);
        assertThat(page2Result.getCurrentPage()).isEqualTo(2);
        
        page2Result.getProcessInstances().forEach(inst -> 
                allPaginatedIds.add((String) inst.get("id")));
        
        // 第三页（剩余记录）
        ProcessMonitorQueryRequest page3Request = ProcessMonitorQueryRequest.builder()
                .processDefinitionKey(processDefinitionKey)
                .limit(pageSize)
                .offset(pageSize * 2)
                .build();
        
        ProcessMonitorResult page3Result = queryProcessInstances(page3Request);
        assertThat(page3Result.isSuccess()).isTrue();
        assertThat(page3Result.getProcessInstances()).hasSize(totalInstances - pageSize * 2);
        assertThat(page3Result.getCurrentPage()).isEqualTo(3);
        
        page3Result.getProcessInstances().forEach(inst -> 
                allPaginatedIds.add((String) inst.get("id")));
        
        // Then: 分页查询应该覆盖所有实例且无重复
        assertThat(allPaginatedIds).hasSize(totalInstances);
        assertThat(allPaginatedIds.stream().distinct().count()).isEqualTo(totalInstances);
        
        for (String instanceId : allInstanceIds) {
            assertThat(allPaginatedIds).contains(instanceId);
        }
    }

    /**
     * 属性测试: 业务键查询准确性
     */
    @Property(tries = 100)
    @Label("业务键查询准确性")
    void businessKeyQueryAccuracy(@ForAll @NotBlank @Size(min = 1, max = 50) String processDefinitionKey,
                                @ForAll @NotBlank @Size(min = 1, max = 30) String targetBusinessKey) {
        // Given: 创建包含目标业务键和其他业务键的流程实例
        Date now = new Date();
        List<String> targetInstanceIds = new ArrayList<>();
        List<String> otherInstanceIds = new ArrayList<>();
        
        // 创建目标业务键的实例
        for (int i = 0; i < 2; i++) {
            String instanceId = "target-bk-" + UUID.randomUUID().toString().substring(0, 8);
            ProcessInstanceStorage instance = new ProcessInstanceStorage(
                    instanceId, processDefinitionKey, targetBusinessKey, "ACTIVE",
                    new Date(now.getTime() - i * 3600000), null, "user" + i, null, false
            );
            processInstances.put(instanceId, instance);
            targetInstanceIds.add(instanceId);
        }
        
        // 创建其他业务键的实例
        for (int i = 0; i < 3; i++) {
            String instanceId = "other-bk-" + UUID.randomUUID().toString().substring(0, 8);
            ProcessInstanceStorage instance = new ProcessInstanceStorage(
                    instanceId, processDefinitionKey, "other-business-" + i, "ACTIVE",
                    new Date(now.getTime() - (i + 10) * 3600000), null, "user" + i, null, false
            );
            processInstances.put(instanceId, instance);
            otherInstanceIds.add(instanceId);
        }
        
        // When: 按业务键查询
        ProcessMonitorQueryRequest request = ProcessMonitorQueryRequest.builder()
                .processDefinitionKey(processDefinitionKey)
                .businessKey(targetBusinessKey)
                .limit(20)
                .offset(0)
                .build();
        
        ProcessMonitorResult result = queryProcessInstances(request);
        
        // Then: 只应该返回匹配业务键的实例
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProcessInstances()).hasSize(2);
        
        for (Map<String, Object> instance : result.getProcessInstances()) {
            String instanceId = (String) instance.get("id");
            String businessKey = (String) instance.get("businessKey");
            
            assertThat(businessKey).isEqualTo(targetBusinessKey);
            assertThat(targetInstanceIds).contains(instanceId);
            assertThat(otherInstanceIds).doesNotContain(instanceId);
        }
    }

    /**
     * 属性测试: 统计数据一致性
     */
    @Property(tries = 100)
    @Label("统计数据一致性")
    void statisticsConsistency(@ForAll @NotBlank @Size(min = 1, max = 50) String processDefinitionKey) {
        // Given: 创建已知数量的流程实例和任务
        Date now = new Date();
        int processCount = 5;
        int taskCount = 8;
        
        // 创建流程实例
        for (int i = 0; i < processCount; i++) {
            String instanceId = "stats-proc-" + UUID.randomUUID().toString().substring(0, 8);
            String status = i % 2 == 0 ? "ACTIVE" : "COMPLETED";
            Date startTime = new Date(now.getTime() - i * 3600000);
            Date endTime = "COMPLETED".equals(status) ? new Date(startTime.getTime() + 1800000) : null;
            Long duration = endTime != null ? (endTime.getTime() - startTime.getTime()) : null;
            
            ProcessInstanceStorage instance = new ProcessInstanceStorage(
                    instanceId, processDefinitionKey, "business-stats-" + i, status,
                    startTime, endTime, "user" + i, duration, false
            );
            processInstances.put(instanceId, instance);
        }
        
        // 创建任务
        for (int i = 0; i < taskCount; i++) {
            String taskId = "stats-task-" + UUID.randomUUID().toString().substring(0, 8);
            String status = i % 3 == 0 ? "PENDING" : (i % 3 == 1 ? "COMPLETED" : "OVERDUE");
            Date createTime = new Date(now.getTime() - i * 3600000);
            Date completeTime = "COMPLETED".equals(status) ? new Date(createTime.getTime() + 1800000) : null;
            Long duration = completeTime != null ? (completeTime.getTime() - createTime.getTime()) : null;
            
            TaskStorage task = new TaskStorage(
                    taskId, "Stats Task " + i, "user" + (i % 3), "proc-stats-" + i, processDefinitionKey,
                    status, createTime, completeTime, null, duration
            );
            tasks.put(taskId, task);
        }
        
        // When: 获取各种统计信息
        ProcessStatisticsResult processStats = getProcessStatistics(processDefinitionKey, null, null);
        TaskStatisticsResult taskStats = getTaskStatistics(null, processDefinitionKey, null, null);
        PerformanceMetricsResult perfMetrics = getPerformanceMetrics(processDefinitionKey, null, null);
        
        // Then: 统计数据应该内部一致
        
        // 流程统计一致性
        long expectedActiveCount = processInstances.values().stream()
                .filter(p -> processDefinitionKey.equals(p.getProcessDefinitionKey()) && "ACTIVE".equals(p.getStatus()))
                .count();
        long expectedCompletedCount = processInstances.values().stream()
                .filter(p -> processDefinitionKey.equals(p.getProcessDefinitionKey()) && "COMPLETED".equals(p.getStatus()))
                .count();
        
        assertThat(processStats.getActiveCount()).isEqualTo(expectedActiveCount);
        assertThat(processStats.getCompletedCount()).isEqualTo(expectedCompletedCount);
        assertThat(processStats.getTotalCount()).isEqualTo(expectedActiveCount + expectedCompletedCount + processStats.getTerminatedCount());
        
        // 任务统计一致性
        long expectedPendingTasks = tasks.values().stream()
                .filter(t -> processDefinitionKey.equals(t.getProcessDefinitionKey()) && "PENDING".equals(t.getStatus()))
                .count();
        long expectedCompletedTasks = tasks.values().stream()
                .filter(t -> processDefinitionKey.equals(t.getProcessDefinitionKey()) && "COMPLETED".equals(t.getStatus()))
                .count();
        
        assertThat(taskStats.getPendingCount()).isEqualTo(expectedPendingTasks);
        assertThat(taskStats.getCompletedCount()).isEqualTo(expectedCompletedTasks);
        assertThat(taskStats.getTotalCount()).isEqualTo(expectedPendingTasks + expectedCompletedTasks);
        
        // 性能指标合理性
        assertThat(perfMetrics.getAverageProcessDuration()).isGreaterThanOrEqualTo(0.0);
        assertThat(perfMetrics.getProcessSuccessRate()).isBetween(0.0, 100.0);
        assertThat(perfMetrics.getMaxProcessDuration()).isGreaterThanOrEqualTo(perfMetrics.getMinProcessDuration());
    }

    // ==================== 辅助方法 ====================
    
    /**
     * 查询流程实例（模拟ProcessMonitorComponent的queryProcessInstances方法）
     */
    private ProcessMonitorResult queryProcessInstances(ProcessMonitorQueryRequest request) {
        try {
            List<Map<String, Object>> result = new ArrayList<>();
            
            // 过滤流程实例
            List<ProcessInstanceStorage> filteredInstances = processInstances.values().stream()
                    .filter(instance -> {
                        // 流程定义键过滤
                        if (request.getProcessDefinitionKey() != null && 
                            !request.getProcessDefinitionKey().equals(instance.getProcessDefinitionKey())) {
                            return false;
                        }
                        
                        // 业务键过滤
                        if (request.getBusinessKey() != null && 
                            !request.getBusinessKey().equals(instance.getBusinessKey())) {
                            return false;
                        }
                        
                        // 状态过滤
                        if (request.getStatus() != null && 
                            !request.getStatus().equals(instance.getStatus())) {
                            return false;
                        }
                        
                        // 时间范围过滤
                        if (request.getStartTime() != null && 
                            instance.getStartTime().before(request.getStartTime())) {
                            return false;
                        }
                        
                        if (request.getEndTime() != null && 
                            instance.getStartTime().after(request.getEndTime())) {
                            return false;
                        }
                        
                        return true;
                    })
                    .collect(Collectors.toList());
            
            // 排序
            filteredInstances.sort((a, b) -> {
                int result1 = a.getId().compareTo(b.getId());
                return "DESC".equalsIgnoreCase(request.getOrderDirection()) ? -result1 : result1;
            });
            
            // 分页
            long totalCount = filteredInstances.size();
            int offset = request.getOffset() != null ? request.getOffset() : 0;
            int limit = request.getLimit() != null ? request.getLimit() : 20;
            
            List<ProcessInstanceStorage> pagedInstances = filteredInstances.stream()
                    .skip(offset)
                    .limit(limit)
                    .collect(Collectors.toList());
            
            // 转换为结果格式
            for (ProcessInstanceStorage instance : pagedInstances) {
                Map<String, Object> instanceInfo = new HashMap<>();
                instanceInfo.put("id", instance.getId());
                instanceInfo.put("processDefinitionKey", instance.getProcessDefinitionKey());
                instanceInfo.put("businessKey", instance.getBusinessKey());
                instanceInfo.put("status", instance.getStatus());
                instanceInfo.put("startTime", instance.getStartTime());
                instanceInfo.put("endTime", instance.getEndTime());
                instanceInfo.put("startUserId", instance.getStartUserId());
                instanceInfo.put("durationInMillis", instance.getDurationInMillis());
                instanceInfo.put("suspended", instance.isSuspended());
                result.add(instanceInfo);
            }
            
            return ProcessMonitorResult.builder()
                    .success(true)
                    .processInstances(result)
                    .totalCount(totalCount)
                    .currentPage(limit > 0 ? (offset / limit) + 1 : 1)
                    .pageSize(limit)
                    .build();
                    
        } catch (Exception e) {
            return ProcessMonitorResult.builder()
                    .success(false)
                    .errorMessage("查询失败: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 获取流程统计信息（模拟ProcessMonitorComponent的getProcessStatistics方法）
     */
    private ProcessStatisticsResult getProcessStatistics(String processDefinitionKey, Date startTime, Date endTime) {
        try {
            // 过滤流程实例
            List<ProcessInstanceStorage> filteredInstances = processInstances.values().stream()
                    .filter(instance -> {
                        if (processDefinitionKey != null && 
                            !processDefinitionKey.equals(instance.getProcessDefinitionKey())) {
                            return false;
                        }
                        
                        if (startTime != null && instance.getStartTime().before(startTime)) {
                            return false;
                        }
                        
                        if (endTime != null && instance.getStartTime().after(endTime)) {
                            return false;
                        }
                        
                        return true;
                    })
                    .collect(Collectors.toList());
            
            // 统计各种状态
            long activeCount = filteredInstances.stream()
                    .filter(instance -> "ACTIVE".equals(instance.getStatus()))
                    .count();
            
            long completedCount = filteredInstances.stream()
                    .filter(instance -> "COMPLETED".equals(instance.getStatus()))
                    .count();
            
            long terminatedCount = filteredInstances.stream()
                    .filter(instance -> "TERMINATED".equals(instance.getStatus()))
                    .count();
            
            // 状态统计
            Map<String, Long> statusStatistics = new HashMap<>();
            statusStatistics.put("ACTIVE", activeCount);
            statusStatistics.put("COMPLETED", completedCount);
            statusStatistics.put("TERMINATED", terminatedCount);
            
            // 流程定义统计
            Map<String, Long> processDefinitionStatistics = filteredInstances.stream()
                    .collect(Collectors.groupingBy(
                            ProcessInstanceStorage::getProcessDefinitionKey,
                            Collectors.counting()
                    ));
            
            // 时间统计（简化实现）
            Map<String, Long> timeStatistics = new HashMap<>();
            LocalDateTime now = LocalDateTime.now();
            for (int i = 6; i >= 0; i--) {
                LocalDateTime dayStart = now.minusDays(i).withHour(0).withMinute(0).withSecond(0);
                LocalDateTime dayEnd = dayStart.withHour(23).withMinute(59).withSecond(59);
                
                Date dayStartDate = Date.from(dayStart.atZone(ZoneId.systemDefault()).toInstant());
                Date dayEndDate = Date.from(dayEnd.atZone(ZoneId.systemDefault()).toInstant());
                
                long dayCount = filteredInstances.stream()
                        .filter(instance -> !instance.getStartTime().before(dayStartDate) && 
                                          !instance.getStartTime().after(dayEndDate))
                        .count();
                
                timeStatistics.put(dayStart.toLocalDate().toString(), dayCount);
            }
            
            return ProcessStatisticsResult.builder()
                    .totalCount(activeCount + completedCount + terminatedCount)
                    .activeCount(activeCount)
                    .completedCount(completedCount)
                    .terminatedCount(terminatedCount)
                    .statusStatistics(statusStatistics)
                    .processDefinitionStatistics(processDefinitionStatistics)
                    .timeStatistics(timeStatistics)
                    .build();
                    
        } catch (Exception e) {
            throw new RuntimeException("获取流程统计信息失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取任务统计信息（模拟ProcessMonitorComponent的getTaskStatistics方法）
     */
    private TaskStatisticsResult getTaskStatistics(String assignee, String processDefinitionKey, Date startTime, Date endTime) {
        try {
            // 过滤任务
            List<TaskStorage> filteredTasks = tasks.values().stream()
                    .filter(task -> {
                        if (assignee != null && !assignee.equals(task.getAssignee())) {
                            return false;
                        }
                        
                        if (processDefinitionKey != null && 
                            !processDefinitionKey.equals(task.getProcessDefinitionKey())) {
                            return false;
                        }
                        
                        if (startTime != null && task.getCompleteTime() != null && 
                            task.getCompleteTime().before(startTime)) {
                            return false;
                        }
                        
                        if (endTime != null && task.getCompleteTime() != null && 
                            task.getCompleteTime().after(endTime)) {
                            return false;
                        }
                        
                        return true;
                    })
                    .collect(Collectors.toList());
            
            // 统计各种状态
            long pendingCount = filteredTasks.stream()
                    .filter(task -> "PENDING".equals(task.getStatus()))
                    .count();
            
            long completedCount = filteredTasks.stream()
                    .filter(task -> "COMPLETED".equals(task.getStatus()))
                    .count();
            
            long overdueCount = filteredTasks.stream()
                    .filter(task -> "OVERDUE".equals(task.getStatus()))
                    .count();
            
            // 任务名称统计（只统计已完成任务）
            Map<String, Long> taskNameStatistics = filteredTasks.stream()
                    .filter(task -> "COMPLETED".equals(task.getStatus()))
                    .collect(Collectors.groupingBy(
                            TaskStorage::getName,
                            Collectors.counting()
                    ));
            
            // 分配人统计（只统计已完成任务）
            Map<String, Long> assigneeStatistics = filteredTasks.stream()
                    .filter(task -> "COMPLETED".equals(task.getStatus()) && task.getAssignee() != null)
                    .collect(Collectors.groupingBy(
                            TaskStorage::getAssignee,
                            Collectors.counting()
                    ));
            
            // 平均处理时间
            double averageProcessingTime = filteredTasks.stream()
                    .filter(task -> "COMPLETED".equals(task.getStatus()) && task.getDurationInMillis() != null)
                    .mapToLong(TaskStorage::getDurationInMillis)
                    .average()
                    .orElse(0.0) / 1000.0; // 转换为秒
            
            return TaskStatisticsResult.builder()
                    .totalCount(pendingCount + completedCount)
                    .pendingCount(pendingCount)
                    .completedCount(completedCount)
                    .overdueCount(overdueCount)
                    .taskNameStatistics(taskNameStatistics)
                    .assigneeStatistics(assigneeStatistics)
                    .averageProcessingTime(averageProcessingTime)
                    .build();
                    
        } catch (Exception e) {
            throw new RuntimeException("获取任务统计信息失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取性能指标（模拟ProcessMonitorComponent的getPerformanceMetrics方法）
     */
    private PerformanceMetricsResult getPerformanceMetrics(String processDefinitionKey, Date startTime, Date endTime) {
        try {
            // 过滤已完成的流程实例
            List<ProcessInstanceStorage> completedInstances = processInstances.values().stream()
                    .filter(instance -> {
                        if (!"COMPLETED".equals(instance.getStatus())) {
                            return false;
                        }
                        
                        if (processDefinitionKey != null && 
                            !processDefinitionKey.equals(instance.getProcessDefinitionKey())) {
                            return false;
                        }
                        
                        if (startTime != null && instance.getStartTime().before(startTime)) {
                            return false;
                        }
                        
                        if (endTime != null && instance.getStartTime().after(endTime)) {
                            return false;
                        }
                        
                        return instance.getDurationInMillis() != null;
                    })
                    .collect(Collectors.toList());
            
            // 计算性能指标
            Double averageProcessDuration = 0.0;
            Long maxProcessDuration = 0L;
            Long minProcessDuration = 0L;
            
            if (!completedInstances.isEmpty()) {
                averageProcessDuration = completedInstances.stream()
                        .mapToLong(ProcessInstanceStorage::getDurationInMillis)
                        .average()
                        .orElse(0.0) / 1000.0; // 转换为秒
                
                maxProcessDuration = completedInstances.stream()
                        .mapToLong(ProcessInstanceStorage::getDurationInMillis)
                        .max()
                        .orElse(0L) / 1000; // 转换为秒
                
                minProcessDuration = completedInstances.stream()
                        .mapToLong(ProcessInstanceStorage::getDurationInMillis)
                        .min()
                        .orElse(0L) / 1000; // 转换为秒
            }
            
            // 计算成功率
            long totalInstances = processInstances.values().stream()
                    .filter(instance -> {
                        if (processDefinitionKey != null && 
                            !processDefinitionKey.equals(instance.getProcessDefinitionKey())) {
                            return false;
                        }
                        return true;
                    })
                    .count();
            
            Double processSuccessRate = totalInstances > 0 ? 
                    (double) completedInstances.size() / totalInstances * 100.0 : 0.0;
            
            // 其他指标（简化实现）
            Double averageTaskWaitTime = 0.0;
            Double throughputPerHour = 0.0;
            
            if (startTime != null && endTime != null) {
                long durationHours = (endTime.getTime() - startTime.getTime()) / (1000 * 60 * 60);
                if (durationHours > 0) {
                    throughputPerHour = (double) completedInstances.size() / durationHours;
                }
            }
            
            // 资源利用率（模拟数据）
            Map<String, Double> resourceUtilization = new HashMap<>();
            resourceUtilization.put("cpu", 65.5);
            resourceUtilization.put("memory", 72.3);
            resourceUtilization.put("disk", 45.8);
            resourceUtilization.put("network", 23.1);
            
            return PerformanceMetricsResult.builder()
                    .averageProcessDuration(averageProcessDuration)
                    .maxProcessDuration(maxProcessDuration)
                    .minProcessDuration(minProcessDuration)
                    .processSuccessRate(processSuccessRate)
                    .averageTaskWaitTime(averageTaskWaitTime)
                    .throughputPerHour(throughputPerHour)
                    .resourceUtilization(resourceUtilization)
                    .build();
                    
        } catch (Exception e) {
            throw new RuntimeException("获取性能指标失败: " + e.getMessage(), e);
        }
    }
}