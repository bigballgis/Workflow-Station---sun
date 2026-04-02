package com.workflow.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.MultiInstanceCancelResult;
import com.workflow.entity.AuditLog;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import com.workflow.repository.AuditLogRepository;
import com.workflow.repository.ExtendedTaskInfoRepository;
import net.jqwik.api.*;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ExecutionQuery;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MultiInstanceCanceller 审计日志属性测试
 * 
 * 使用 jqwik 进行基于属性的测试，验证取消操作的审计日志完整性
 * 
 * Feature: multi-instance-task-dispatch
 */
class MultiInstanceCancellerAuditLogPropertyTest {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Property 18: 取消审计日志完整性
     * 
     * For any 多实例子流程取消操作，审计日志应包含：被取消的子任务数量、各子任务的处理人 ID 和取消前状态。
     * 
     * **Validates: Requirements 9.4**
     */
    @Property(tries = 100)
    @Label("Property 18: 取消审计日志完整性 - 审计日志包含完整的取消信息")
    void property18_auditLogCompleteness(
        @ForAll("taskScenariosWithActiveTasks") TaskScenario scenario
    ) {
        // Setup mocks
        RuntimeService runtimeService = mock(RuntimeService.class);
        ExtendedTaskInfoRepository extendedTaskInfoRepository = mock(ExtendedTaskInfoRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        ExecutionQuery executionQuery = mock(ExecutionQuery.class);
        
        MultiInstanceCanceller canceller = new MultiInstanceCanceller();
        injectMocks(canceller, runtimeService, extendedTaskInfoRepository, auditLogRepository);
        
        // Given: 准备流程实例中的多实例执行和任务
        String processInstanceId = scenario.processInstanceId;
        
        // 模拟 Flowable 执行查询
        when(runtimeService.createExecutionQuery()).thenReturn(executionQuery);
        when(executionQuery.processInstanceId(processInstanceId)).thenReturn(executionQuery);
        when(executionQuery.list()).thenReturn(scenario.executions);
        
        // 模拟 ExtendedTaskInfo 查询
        when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(processInstanceId))
            .thenReturn(scenario.tasks);
        
        // 模拟任务保存（返回保存的任务）
        when(extendedTaskInfoRepository.save(any(ExtendedTaskInfo.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // 捕获保存的审计日志
        AuditLog[] capturedAuditLog = new AuditLog[1];
        when(auditLogRepository.save(any(AuditLog.class)))
            .thenAnswer(invocation -> {
                capturedAuditLog[0] = invocation.getArgument(0);
                return capturedAuditLog[0];
            });
        
        // 计算预期的取消任务信息（在执行取消操作之前）
        List<ExtendedTaskInfo> expectedCancelledTasks = scenario.tasks.stream()
            .filter(task -> isMultiInstanceTask(task))
            .filter(task -> !isCompletedOrCancelled(task))
            .collect(Collectors.toList());
        
        int expectedCancelledCount = expectedCancelledTasks.size();
        
        // 保存每个任务的原始状态（在取消操作之前）
        Map<String, String> originalStatuses = expectedCancelledTasks.stream()
            .collect(Collectors.toMap(
                ExtendedTaskInfo::getTaskId,
                ExtendedTaskInfo::getStatus
            ));
        
        Map<String, String> originalAssignees = expectedCancelledTasks.stream()
            .collect(Collectors.toMap(
                ExtendedTaskInfo::getTaskId,
                ExtendedTaskInfo::getAssignmentTarget
            ));
        
        // When: 执行取消操作
        MultiInstanceCancelResult result = canceller.cancelMultiInstanceTasks(processInstanceId);
        
        // Then: 验证审计日志被记录
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
        
        // 验证审计日志不为空
        assertThat(capturedAuditLog[0])
            .as("审计日志应该被创建")
            .isNotNull();
        
        AuditLog auditLog = capturedAuditLog[0];
        
        // 验证审计日志基本字段
        assertThat(auditLog.getId())
            .as("审计日志应该有 ID")
            .isNotNull();
        
        assertThat(auditLog.getUserId())
            .as("审计日志应该记录操作用户")
            .isEqualTo("SYSTEM");
        
        assertThat(auditLog.getOperationType())
            .as("审计日志操作类型应该是 CANCEL")
            .isEqualTo("CANCEL");
        
        assertThat(auditLog.getResourceType())
            .as("审计日志资源类型应该是 MULTI_INSTANCE_TASKS")
            .isEqualTo("MULTI_INSTANCE_TASKS");
        
        assertThat(auditLog.getResourceId())
            .as("审计日志资源 ID 应该是流程实例 ID")
            .isEqualTo(processInstanceId);
        
        assertThat(auditLog.getOperationResult())
            .as("审计日志操作结果应该是 SUCCESS")
            .isEqualTo("SUCCESS");
        
        assertThat(auditLog.getTimestamp())
            .as("审计日志应该有时间戳")
            .isNotNull();
        
        // 验证审计日志的 contextData 字段包含完整的取消信息
        assertThat(auditLog.getContextData())
            .as("审计日志应该包含上下文数据")
            .isNotNull();
        
        // 解析 contextData JSON
        Map<String, Object> contextData = parseContextData(auditLog.getContextData());
        
        // 验证 contextData 包含被取消的子任务数量
        assertThat(contextData)
            .as("上下文数据应该包含 cancelledCount 字段")
            .containsKey("cancelledCount");
        
        int actualCancelledCount = ((Number) contextData.get("cancelledCount")).intValue();
        assertThat(actualCancelledCount)
            .as("审计日志中的取消数量应该等于实际取消的任务数量")
            .isEqualTo(expectedCancelledCount);
        
        // 验证 contextData 包含失败数量
        assertThat(contextData)
            .as("上下文数据应该包含 failedCount 字段")
            .containsKey("failedCount");
        
        int actualFailedCount = ((Number) contextData.get("failedCount")).intValue();
        assertThat(actualFailedCount)
            .as("审计日志中的失败数量应该为 0")
            .isEqualTo(0);
        
        // 验证 contextData 包含取消任务详情列表
        assertThat(contextData)
            .as("上下文数据应该包含 cancelledTasks 字段")
            .containsKey("cancelledTasks");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cancelledTasksData = (List<Map<String, Object>>) contextData.get("cancelledTasks");
        
        assertThat(cancelledTasksData)
            .as("取消任务详情列表不应该为空")
            .isNotNull()
            .hasSize(expectedCancelledCount);
        
        // 验证每个取消任务详情包含必要的字段
        for (Map<String, Object> taskDetail : cancelledTasksData) {
            // 验证包含任务 ID
            assertThat(taskDetail)
                .as("任务详情应该包含 taskId 字段")
                .containsKey("taskId");
            
            String taskId = (String) taskDetail.get("taskId");
            assertThat(taskId)
                .as("任务 ID 不应该为空")
                .isNotNull()
                .isNotEmpty();
            
            // 验证包含处理人 ID
            assertThat(taskDetail)
                .as("任务详情应该包含 assigneeId 字段")
                .containsKey("assigneeId");
            
            String assigneeId = (String) taskDetail.get("assigneeId");
            assertThat(assigneeId)
                .as("处理人 ID 不应该为空")
                .isNotNull()
                .isNotEmpty();
            
            // 验证包含取消前状态
            assertThat(taskDetail)
                .as("任务详情应该包含 previousStatus 字段")
                .containsKey("previousStatus");
            
            String previousStatus = (String) taskDetail.get("previousStatus");
            assertThat(previousStatus)
                .as("取消前状态不应该为空")
                .isNotNull()
                .isNotEmpty();
            
            // 验证包含子表行 ID
            assertThat(taskDetail)
                .as("任务详情应该包含 subTableRowId 字段")
                .containsKey("subTableRowId");
            
            Object subTableRowId = taskDetail.get("subTableRowId");
            assertThat(subTableRowId)
                .as("子表行 ID 不应该为空")
                .isNotNull();
            
            // 验证包含子表名称
            assertThat(taskDetail)
                .as("任务详情应该包含 subTableName 字段")
                .containsKey("subTableName");
            
            String subTableName = (String) taskDetail.get("subTableName");
            assertThat(subTableName)
                .as("子表名称不应该为空")
                .isNotNull()
                .isNotEmpty();
        }
        
        // 验证审计日志中的任务详情与实际取消的任务匹配
        List<String> expectedTaskIds = expectedCancelledTasks.stream()
            .map(ExtendedTaskInfo::getTaskId)
            .sorted()
            .collect(Collectors.toList());
        
        List<String> actualTaskIds = cancelledTasksData.stream()
            .map(detail -> (String) detail.get("taskId"))
            .sorted()
            .collect(Collectors.toList());
        
        assertThat(actualTaskIds)
            .as("审计日志中的任务 ID 列表应该与实际取消的任务匹配")
            .isEqualTo(expectedTaskIds);
        
        // 验证每个任务的处理人 ID 和取消前状态正确
        // 使用保存的原始状态进行验证
        for (String taskId : originalStatuses.keySet()) {
            Map<String, Object> matchingDetail = cancelledTasksData.stream()
                .filter(detail -> taskId.equals(detail.get("taskId")))
                .findFirst()
                .orElse(null);
            
            assertThat(matchingDetail)
                .as("应该找到任务 %s 的审计详情", taskId)
                .isNotNull();
            
            String expectedAssignee = originalAssignees.get(taskId);
            assertThat(matchingDetail.get("assigneeId"))
                .as("任务 %s 的处理人 ID 应该匹配", taskId)
                .isEqualTo(expectedAssignee);
            
            // previousStatus 应该是取消前的原始状态
            String expectedPreviousStatus = originalStatuses.get(taskId);
            assertThat(matchingDetail.get("previousStatus"))
                .as("任务 %s 的取消前状态应该是 %s", taskId, expectedPreviousStatus)
                .isEqualTo(expectedPreviousStatus);
        }
    }
    
    /**
     * Property: 无活跃子任务时不记录审计日志
     * 
     * For any 流程实例，如果没有活跃的多实例子任务，取消操作应该静默跳过，
     * 不记录审计日志。
     */
    @Property(tries = 100)
    @Label("无活跃子任务时不记录审计日志")
    void shouldNotRecordAuditLogWhenNoActiveTasks(
        @ForAll("processInstanceIds") String processInstanceId,
        @ForAll("emptyOrCompletedTaskScenarios") TaskScenario scenario
    ) {
        // Setup mocks
        RuntimeService runtimeService = mock(RuntimeService.class);
        ExtendedTaskInfoRepository extendedTaskInfoRepository = mock(ExtendedTaskInfoRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        ExecutionQuery executionQuery = mock(ExecutionQuery.class);
        
        MultiInstanceCanceller canceller = new MultiInstanceCanceller();
        injectMocks(canceller, runtimeService, extendedTaskInfoRepository, auditLogRepository);
        
        // Given: 没有活跃的多实例执行或所有任务都已完成
        when(runtimeService.createExecutionQuery()).thenReturn(executionQuery);
        when(executionQuery.processInstanceId(processInstanceId)).thenReturn(executionQuery);
        when(executionQuery.list()).thenReturn(scenario.executions);
        
        when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(processInstanceId))
            .thenReturn(scenario.tasks);
        
        // When: 执行取消操作
        MultiInstanceCancelResult result = canceller.cancelMultiInstanceTasks(processInstanceId);
        
        // Then: 验证没有记录审计日志
        verify(auditLogRepository, never()).save(any(AuditLog.class));
        
        assertThat(result.getCancelledCount()).isEqualTo(0);
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 任务场景数据结构
     */
    private static class TaskScenario {
        final String processInstanceId;
        final List<Execution> executions;
        final List<ExtendedTaskInfo> tasks;
        
        TaskScenario(String processInstanceId, List<Execution> executions, List<ExtendedTaskInfo> tasks) {
            this.processInstanceId = processInstanceId;
            this.executions = executions;
            this.tasks = tasks;
        }
    }
    
    @Provide
    Arbitrary<TaskScenario> taskScenariosWithActiveTasks() {
        return Combinators.combine(
            processInstanceIds(),
            Arbitraries.integers().between(1, 10), // 活跃的多实例子任务数量（至少1个）
            Arbitraries.integers().between(0, 5),  // 已完成的多实例子任务数量
            Arbitraries.integers().between(0, 3)   // 非多实例任务数量
        ).as((processInstanceId, activeCount, completedCount, nonMiCount) -> {
            List<Execution> executions = new ArrayList<>();
            List<ExtendedTaskInfo> tasks = new ArrayList<>();
            
            // 创建活跃的多实例执行
            for (int i = 0; i < activeCount; i++) {
                Execution execution = mock(Execution.class);
                when(execution.getId()).thenReturn("exec-" + i);
                when(execution.getActivityId()).thenReturn("MultiInstance_SubTable_45");
                executions.add(execution);
            }
            
            // 创建活跃的多实例子任务（使用不同的状态）
            String[] activeStatuses = {"ASSIGNED", "IN_PROGRESS", "PENDING"};
            for (int i = 0; i < activeCount; i++) {
                String status = activeStatuses[i % activeStatuses.length];
                tasks.add(createMultiInstanceTask(
                    processInstanceId,
                    "task-active-" + i,
                    "user-" + i,
                    100L + i,
                    status
                ));
            }
            
            // 创建已完成的多实例子任务
            for (int i = 0; i < completedCount; i++) {
                tasks.add(createMultiInstanceTask(
                    processInstanceId,
                    "task-completed-" + i,
                    "user-" + (activeCount + i),
                    200L + i,
                    "COMPLETED"
                ));
            }
            
            // 创建非多实例任务
            for (int i = 0; i < nonMiCount; i++) {
                tasks.add(createNonMultiInstanceTask(
                    processInstanceId,
                    "task-non-mi-" + i,
                    "user-" + (activeCount + completedCount + i),
                    "ASSIGNED"
                ));
            }
            
            return new TaskScenario(processInstanceId, executions, tasks);
        });
    }
    
    @Provide
    Arbitrary<TaskScenario> emptyOrCompletedTaskScenarios() {
        return Combinators.combine(
            processInstanceIds(),
            Arbitraries.integers().between(0, 5)  // 已完成的多实例子任务数量
        ).as((processInstanceId, completedCount) -> {
            List<Execution> executions = new ArrayList<>();
            List<ExtendedTaskInfo> tasks = new ArrayList<>();
            
            // 创建已完成的多实例子任务
            for (int i = 0; i < completedCount; i++) {
                tasks.add(createMultiInstanceTask(
                    processInstanceId,
                    "task-completed-" + i,
                    "user-" + i,
                    100L + i,
                    "COMPLETED"
                ));
            }
            
            return new TaskScenario(processInstanceId, executions, tasks);
        });
    }
    
    @Provide
    Arbitrary<String> processInstanceIds() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .ofMinLength(5)
            .ofMaxLength(20)
            .map(s -> "proc-" + s);
    }
    
    private static ExtendedTaskInfo createMultiInstanceTask(
        String processInstanceId,
        String taskId,
        String assigneeId,
        Long rowId,
        String status
    ) {
        String extendedProperties = String.format(
            "{\"multiInstance\":true,\"subTableRowId\":%d,\"subTableRowVersion\":1," +
            "\"subTableId\":\"45\",\"subTableName\":\"fu_participants\"}",
            rowId
        );
        
        return ExtendedTaskInfo.builder()
            .id((long) taskId.hashCode())
            .taskId(taskId)
            .processInstanceId(processInstanceId)
            .processDefinitionId("proc-def-001")
            .taskDefinitionKey("fillInfo")
            .taskName("填写参会信息")
            .assignmentType(AssignmentType.USER)
            .assignmentTarget(assigneeId)
            .status(status)
            .createdTime(LocalDateTime.now())
            .extendedProperties(extendedProperties)
            .isDeleted(false)
            .build();
    }
    
    private static ExtendedTaskInfo createNonMultiInstanceTask(
        String processInstanceId,
        String taskId,
        String assigneeId,
        String status
    ) {
        String extendedProperties = "{\"someProperty\":\"value\"}";
        
        return ExtendedTaskInfo.builder()
            .id((long) taskId.hashCode())
            .taskId(taskId)
            .processInstanceId(processInstanceId)
            .processDefinitionId("proc-def-001")
            .taskDefinitionKey("normalTask")
            .taskName("普通任务")
            .assignmentType(AssignmentType.USER)
            .assignmentTarget(assigneeId)
            .status(status)
            .createdTime(LocalDateTime.now())
            .extendedProperties(extendedProperties)
            .isDeleted(false)
            .build();
    }
    
    private boolean isMultiInstanceTask(ExtendedTaskInfo task) {
        String extendedProperties = task.getExtendedProperties();
        if (extendedProperties == null || extendedProperties.trim().isEmpty()) {
            return false;
        }
        
        try {
            Map<String, Object> properties = objectMapper.readValue(
                extendedProperties,
                new TypeReference<Map<String, Object>>() {}
            );
            
            Object multiInstance = properties.get("multiInstance");
            return multiInstance != null && Boolean.TRUE.equals(multiInstance);
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean isCompletedOrCancelled(ExtendedTaskInfo task) {
        String status = task.getStatus();
        return "COMPLETED".equals(status) || "CANCELLED".equals(status);
    }
    
    private Map<String, Object> parseContextData(String contextData) {
        try {
            return objectMapper.readValue(
                contextData,
                new TypeReference<Map<String, Object>>() {}
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse contextData JSON", e);
        }
    }
    
    private void injectMocks(MultiInstanceCanceller canceller,
                            RuntimeService runtimeService,
                            ExtendedTaskInfoRepository extendedTaskInfoRepository,
                            AuditLogRepository auditLogRepository) {
        try {
            java.lang.reflect.Field runtimeServiceField = 
                MultiInstanceCanceller.class.getDeclaredField("runtimeService");
            runtimeServiceField.setAccessible(true);
            runtimeServiceField.set(canceller, runtimeService);
            
            java.lang.reflect.Field extendedTaskInfoRepositoryField = 
                MultiInstanceCanceller.class.getDeclaredField("extendedTaskInfoRepository");
            extendedTaskInfoRepositoryField.setAccessible(true);
            extendedTaskInfoRepositoryField.set(canceller, extendedTaskInfoRepository);
            
            java.lang.reflect.Field auditLogRepositoryField = 
                MultiInstanceCanceller.class.getDeclaredField("auditLogRepository");
            auditLogRepositoryField.setAccessible(true);
            auditLogRepositoryField.set(canceller, auditLogRepository);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mocks", e);
        }
    }
}
