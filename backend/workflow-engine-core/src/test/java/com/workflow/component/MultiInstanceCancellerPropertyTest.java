package com.workflow.component;

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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MultiInstanceCanceller 属性测试
 * 
 * 使用 jqwik 进行基于属性的测试，验证跨所有输入的通用属性
 * 
 * Feature: multi-instance-task-dispatch
 */
class MultiInstanceCancellerPropertyTest {
    
    /**
     * Property 16: 级联取消正确性
     * 
     * For any 包含活跃多实例子任务的流程实例，当主流程被终止或撤回到多实例之前的节点时，
     * 所有未完成的子任务 ExtendedTaskInfo 记录状态应更新为 CANCELLED。
     * 
     * **Validates: Requirements 9.1, 9.2**
     */
    @Property(tries = 100)
    @Label("Property 16: 级联取消正确性 - 所有未完成子任务被取消")
    void property16_cascadingCancellationCorrectness(
        @ForAll("taskScenarios") TaskScenario scenario
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
        
        // When: 执行取消操作
        // 注意：先计算预期的取消数量（在执行取消操作之前），因为取消操作会修改任务状态
        long expectedCancelledCount = scenario.tasks.stream()
            .filter(task -> isMultiInstanceTask(task))
            .filter(task -> !isCompletedOrCancelled(task))
            .count();
        
        // 保存原始的已完成或已取消任务列表（在执行取消操作之前）
        List<String> originallyCompletedOrCancelledTaskIds = scenario.tasks.stream()
            .filter(task -> isCompletedOrCancelled(task))
            .map(ExtendedTaskInfo::getTaskId)
            .collect(Collectors.toList());
        
        MultiInstanceCancelResult result = canceller.cancelMultiInstanceTasks(processInstanceId);
        
        // Then: 验证取消结果
        assertThat(result).isNotNull();
        
        assertThat(result.getCancelledCount())
            .as("应该取消所有未完成的多实例子任务")
            .isEqualTo(expectedCancelledCount);
        
        assertThat(result.getFailedCount())
            .as("没有失败的取消操作")
            .isEqualTo(0);
        
        // 验证所有未完成的多实例子任务状态都被更新为 CANCELLED
        List<ExtendedTaskInfo> uncompletedMultiInstanceTasks = scenario.tasks.stream()
            .filter(task -> isMultiInstanceTask(task))
            .filter(task -> !isCompletedOrCancelled(task))
            .collect(Collectors.toList());
        
        for (ExtendedTaskInfo task : uncompletedMultiInstanceTasks) {
            verify(extendedTaskInfoRepository).save(argThat(savedTask ->
                savedTask.getTaskId().equals(task.getTaskId()) &&
                "CANCELLED".equals(savedTask.getStatus())
            ));
        }
        
        // 验证已完成或已取消的任务没有被再次更新（使用原始状态）
        for (String taskId : originallyCompletedOrCancelledTaskIds) {
            verify(extendedTaskInfoRepository, never()).save(argThat(savedTask ->
                savedTask.getTaskId().equals(taskId)
            ));
        }
        
        // 验证非多实例任务没有被取消
        List<ExtendedTaskInfo> nonMultiInstanceTasks = scenario.tasks.stream()
            .filter(task -> !isMultiInstanceTask(task))
            .collect(Collectors.toList());
        
        for (ExtendedTaskInfo task : nonMultiInstanceTasks) {
            verify(extendedTaskInfoRepository, never()).save(argThat(savedTask ->
                savedTask.getTaskId().equals(task.getTaskId())
            ));
        }
        
        // 验证取消详情
        assertThat(result.getCancelledTasks())
            .as("取消详情数量应该等于取消的任务数量")
            .hasSize((int) expectedCancelledCount);
        
        // 验证每个取消详情包含必要的信息
        for (MultiInstanceCancelResult.CancelledTaskDetail detail : result.getCancelledTasks()) {
            assertThat(detail.getTaskId()).isNotNull();
            assertThat(detail.getAssigneeId()).isNotNull();
            assertThat(detail.getPreviousStatus()).isNotNull();
            assertThat(detail.getSubTableRowId()).isNotNull();
            assertThat(detail.getSubTableName()).isNotNull();
        }
        
        // 验证审计日志被记录（如果有任务被取消）
        if (expectedCancelledCount > 0) {
            verify(auditLogRepository, times(1)).save(argThat(auditLog ->
                "CANCEL".equals(auditLog.getOperationType()) &&
                "MULTI_INSTANCE_TASKS".equals(auditLog.getResourceType()) &&
                processInstanceId.equals(auditLog.getResourceId()) &&
                "SUCCESS".equals(auditLog.getOperationResult())
            ));
        }
    }
    
    /**
     * Property: 无活跃子任务时静默跳过
     * 
     * For any 流程实例，如果没有活跃的多实例子任务，取消操作应该静默跳过，
     * 不更新任何任务，不记录审计日志。
     */
    @Property(tries = 100)
    @Label("无活跃子任务时静默跳过")
    void shouldSilentlySkipWhenNoActiveTasks(
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
        
        // Then: 验证静默跳过
        assertThat(result.getCancelledCount()).isEqualTo(0);
        assertThat(result.getFailedCount()).isEqualTo(0);
        assertThat(result.getCancelledTasks()).isEmpty();
        
        // 验证没有更新任何任务
        verify(extendedTaskInfoRepository, never()).save(any(ExtendedTaskInfo.class));
        
        // 验证没有记录审计日志
        verify(auditLogRepository, never()).save(any(AuditLog.class));
    }
    
    /**
     * Property: 取消操作幂等性
     * 
     * For any 流程实例，多次执行取消操作应该产生相同的结果（幂等性）。
     */
    @Property(tries = 100)
    @Label("取消操作幂等性")
    void shouldBeIdempotent(
        @ForAll("taskScenarios") TaskScenario scenario
    ) {
        // Setup mocks
        RuntimeService runtimeService = mock(RuntimeService.class);
        ExtendedTaskInfoRepository extendedTaskInfoRepository = mock(ExtendedTaskInfoRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        ExecutionQuery executionQuery = mock(ExecutionQuery.class);
        
        MultiInstanceCanceller canceller = new MultiInstanceCanceller();
        injectMocks(canceller, runtimeService, extendedTaskInfoRepository, auditLogRepository);
        
        String processInstanceId = scenario.processInstanceId;
        
        // Given: 准备流程实例
        when(runtimeService.createExecutionQuery()).thenReturn(executionQuery);
        when(executionQuery.processInstanceId(processInstanceId)).thenReturn(executionQuery);
        when(executionQuery.list()).thenReturn(scenario.executions);
        
        when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(processInstanceId))
            .thenReturn(scenario.tasks);
        
        when(extendedTaskInfoRepository.save(any(ExtendedTaskInfo.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: 第一次执行取消操作
        MultiInstanceCancelResult result1 = canceller.cancelMultiInstanceTasks(processInstanceId);
        
        // 模拟第二次执行时，所有任务都已经是 CANCELLED 状态
        List<ExtendedTaskInfo> cancelledTasks = scenario.tasks.stream()
            .map(task -> {
                if (isMultiInstanceTask(task) && !isCompletedOrCancelled(task)) {
                    ExtendedTaskInfo cancelledTask = ExtendedTaskInfo.builder()
                        .id(task.getId())
                        .taskId(task.getTaskId())
                        .processInstanceId(task.getProcessInstanceId())
                        .processDefinitionId(task.getProcessDefinitionId())
                        .taskDefinitionKey(task.getTaskDefinitionKey())
                        .taskName(task.getTaskName())
                        .assignmentType(task.getAssignmentType())
                        .assignmentTarget(task.getAssignmentTarget())
                        .status("CANCELLED")
                        .createdTime(task.getCreatedTime())
                        .extendedProperties(task.getExtendedProperties())
                        .isDeleted(task.getIsDeleted())
                        .build();
                    return cancelledTask;
                }
                return task;
            })
            .collect(Collectors.toList());
        
        when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(processInstanceId))
            .thenReturn(cancelledTasks);
        
        // When: 第二次执行取消操作
        MultiInstanceCancelResult result2 = canceller.cancelMultiInstanceTasks(processInstanceId);
        
        // Then: 第二次执行应该静默跳过（所有任务都已取消）
        assertThat(result2.getCancelledCount()).isEqualTo(0);
        assertThat(result2.getFailedCount()).isEqualTo(0);
        assertThat(result2.getCancelledTasks()).isEmpty();
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
    Arbitrary<TaskScenario> taskScenarios() {
        return Combinators.combine(
            processInstanceIds(),
            Arbitraries.integers().between(1, 10), // 活跃的多实例子任务数量 (至少1个，确保有executions)
            Arbitraries.integers().between(0, 5),  // 已完成的多实例子任务数量
            Arbitraries.integers().between(0, 3)   // 非多实例任务数量
        ).as((processInstanceId, activeCount, completedCount, nonMiCount) -> {
            List<Execution> executions = new ArrayList<>();
            List<ExtendedTaskInfo> tasks = new ArrayList<>();
            
            // 创建活跃的多实例执行
            // 注意：只有活跃的多实例任务才会有对应的executions（已完成的任务不会有活跃的execution）
            if (activeCount > 0) {
                for (int i = 0; i < activeCount; i++) {
                    Execution execution = mock(Execution.class);
                    when(execution.getId()).thenReturn("exec-" + i);
                    when(execution.getActivityId()).thenReturn("MultiInstance_SubTable_45");
                    executions.add(execution);
                }
            }
            
            // 创建活跃的多实例子任务
            for (int i = 0; i < activeCount; i++) {
                tasks.add(createMultiInstanceTask(
                    processInstanceId,
                    "task-active-" + i,
                    "user-" + i,
                    100L + i,
                    "ASSIGNED"
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
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> properties = mapper.readValue(
                extendedProperties,
                new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {}
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
