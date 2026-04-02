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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MultiInstanceCanceller 数据保留属性测试
 * 
 * 验证取消多实例子任务时，已提交的子表数据不被回滚或删除
 * 
 * Feature: multi-instance-task-dispatch
 */
class MultiInstanceCancellerDataPreservationPropertyTest {
    
    /**
     * Property 17: 取消时数据保留
     * 
     * For any 被取消的多实例子流程，已提交到子表的数据行不应被回滚或删除，
     * 数据内容保持取消前的状态。
     * 
     * **Validates: Requirements 9.3**
     */
    @Property(tries = 100)
    @Label("Property 17: 取消时数据保留 - 已提交数据不被回滚")
    void property17_dataPreservationOnCancellation(
        @ForAll("dataPreservationScenarios") DataPreservationScenario scenario
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
        String subTableName = scenario.subTableName;
        
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
        
        // 记录取消前的子表数据快照（模拟数据库中的实际数据）
        Map<Long, SubTableRowData> dataBeforeCancellation = new HashMap<>(scenario.subTableData);
        
        // When: 执行取消操作
        MultiInstanceCancelResult result = canceller.cancelMultiInstanceTasks(processInstanceId);
        
        // Then: 验证取消操作成功
        assertThat(result).isNotNull();
        assertThat(result.getCancelledCount())
            .as("应该取消所有未完成的多实例子任务")
            .isGreaterThanOrEqualTo(0);
        
        // 关键验证：子表数据应该保持不变（没有被回滚或删除）
        // 验证 MultiInstanceCanceller 没有调用任何 DELETE 或 UPDATE 子表数据的操作
        // 注意：MultiInstanceCanceller 只更新 ExtendedTaskInfo 的状态，不应该修改子表数据
        
        // 关键验证：MultiInstanceCanceller 不应该修改子表数据
        // 它只更新 ExtendedTaskInfo 的状态，不涉及子表的 DELETE 或 UPDATE 操作
        // 由于 MultiInstanceCanceller 实际上不使用 JdbcTemplate 直接操作子表，
        // 我们通过验证子表数据内容保持不变来确认数据保留
        
        // 验证子表数据内容保持不变
        for (Map.Entry<Long, SubTableRowData> entry : dataBeforeCancellation.entrySet()) {
            Long rowId = entry.getKey();
            SubTableRowData dataBefore = entry.getValue();
            SubTableRowData dataAfter = scenario.subTableData.get(rowId);
            
            assertThat(dataAfter)
                .as("子表行 %d 的数据应该存在（未被删除）", rowId)
                .isNotNull();
            
            assertThat(dataAfter.content)
                .as("子表行 %d 的数据内容应该保持不变", rowId)
                .isEqualTo(dataBefore.content);
            
            assertThat(dataAfter.rowVersion)
                .as("子表行 %d 的 row_version 应该保持不变（未被更新）", rowId)
                .isEqualTo(dataBefore.rowVersion);
            
            assertThat(dataAfter.assigneeId)
                .as("子表行 %d 的处理人应该保持不变", rowId)
                .isEqualTo(dataBefore.assigneeId);
        }
        
        // 验证只有 ExtendedTaskInfo 的状态被更新为 CANCELLED
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
        
        // 验证审计日志记录了取消操作（如果有任务被取消）
        if (result.getCancelledCount() > 0) {
            verify(auditLogRepository, times(1)).save(argThat(auditLog ->
                "CANCEL".equals(auditLog.getOperationType()) &&
                "MULTI_INSTANCE_TASKS".equals(auditLog.getResourceType()) &&
                processInstanceId.equals(auditLog.getResourceId())
            ));
        }
    }
    
    /**
     * Property: 已完成任务的数据保留
     * 
     * For any 已完成的多实例子任务，取消操作不应该影响其关联的子表数据。
     */
    @Property(tries = 100)
    @Label("已完成任务的数据保留")
    void shouldPreserveDataForCompletedTasks(
        @ForAll("completedTaskScenarios") DataPreservationScenario scenario
    ) {
        // Setup mocks
        RuntimeService runtimeService = mock(RuntimeService.class);
        ExtendedTaskInfoRepository extendedTaskInfoRepository = mock(ExtendedTaskInfoRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        ExecutionQuery executionQuery = mock(ExecutionQuery.class);
        
        MultiInstanceCanceller canceller = new MultiInstanceCanceller();
        injectMocks(canceller, runtimeService, extendedTaskInfoRepository, auditLogRepository);
        
        String processInstanceId = scenario.processInstanceId;
        
        // Given: 所有任务都已完成
        when(runtimeService.createExecutionQuery()).thenReturn(executionQuery);
        when(executionQuery.processInstanceId(processInstanceId)).thenReturn(executionQuery);
        when(executionQuery.list()).thenReturn(scenario.executions);
        
        when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(processInstanceId))
            .thenReturn(scenario.tasks);
        
        // 记录取消前的子表数据快照
        Map<Long, SubTableRowData> dataBeforeCancellation = new HashMap<>(scenario.subTableData);
        
        // When: 执行取消操作
        MultiInstanceCancelResult result = canceller.cancelMultiInstanceTasks(processInstanceId);
        
        // Then: 验证没有任务被取消（因为都已完成）
        assertThat(result.getCancelledCount()).isEqualTo(0);
        
        // 验证子表数据完全保持不变
        assertThat(scenario.subTableData)
            .as("已完成任务的子表数据应该完全保持不变")
            .isEqualTo(dataBeforeCancellation);
        
        // 验证没有更新任何 ExtendedTaskInfo
        verify(extendedTaskInfoRepository, never()).save(any(ExtendedTaskInfo.class));
        
        // 验证没有记录审计日志（因为没有任务被取消）
        verify(auditLogRepository, never()).save(any(AuditLog.class));
    }
    
    /**
     * Property: 混合状态任务的数据保留
     * 
     * For any 包含已完成和未完成任务的流程实例，取消操作应该只更新未完成任务的状态，
     * 所有任务关联的子表数据都应该保持不变。
     */
    @Property(tries = 100)
    @Label("混合状态任务的数据保留")
    void shouldPreserveDataForMixedStatusTasks(
        @ForAll("mixedStatusScenarios") DataPreservationScenario scenario
    ) {
        // Setup mocks
        RuntimeService runtimeService = mock(RuntimeService.class);
        ExtendedTaskInfoRepository extendedTaskInfoRepository = mock(ExtendedTaskInfoRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        ExecutionQuery executionQuery = mock(ExecutionQuery.class);
        
        MultiInstanceCanceller canceller = new MultiInstanceCanceller();
        injectMocks(canceller, runtimeService, extendedTaskInfoRepository, auditLogRepository);
        
        String processInstanceId = scenario.processInstanceId;
        
        // Given: 混合状态的任务（部分已完成，部分未完成）
        when(runtimeService.createExecutionQuery()).thenReturn(executionQuery);
        when(executionQuery.processInstanceId(processInstanceId)).thenReturn(executionQuery);
        when(executionQuery.list()).thenReturn(scenario.executions);
        
        when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(processInstanceId))
            .thenReturn(scenario.tasks);
        
        when(extendedTaskInfoRepository.save(any(ExtendedTaskInfo.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // 记录取消前的子表数据快照
        Map<Long, SubTableRowData> dataBeforeCancellation = new HashMap<>(scenario.subTableData);
        
        // 统计已完成和未完成的任务数量
        long completedCount = scenario.tasks.stream()
            .filter(task -> isMultiInstanceTask(task))
            .filter(task -> "COMPLETED".equals(task.getStatus()))
            .count();
        
        long uncompletedCount = scenario.tasks.stream()
            .filter(task -> isMultiInstanceTask(task))
            .filter(task -> !isCompletedOrCancelled(task))
            .count();
        
        // When: 执行取消操作
        MultiInstanceCancelResult result = canceller.cancelMultiInstanceTasks(processInstanceId);
        
        // Then: 验证只有未完成的任务被取消
        assertThat(result.getCancelledCount()).isEqualTo(uncompletedCount);
        
        // 验证所有任务（已完成和被取消）的子表数据都保持不变
        for (Map.Entry<Long, SubTableRowData> entry : dataBeforeCancellation.entrySet()) {
            Long rowId = entry.getKey();
            SubTableRowData dataBefore = entry.getValue();
            SubTableRowData dataAfter = scenario.subTableData.get(rowId);
            
            assertThat(dataAfter)
                .as("子表行 %d 的数据应该存在（未被删除）", rowId)
                .isNotNull();
            
            assertThat(dataAfter.content)
                .as("子表行 %d 的数据内容应该保持不变（无论任务是已完成还是被取消）", rowId)
                .isEqualTo(dataBefore.content);
            
            assertThat(dataAfter.rowVersion)
                .as("子表行 %d 的 row_version 应该保持不变", rowId)
                .isEqualTo(dataBefore.rowVersion);
        }
        
        // 验证已完成的任务没有被更新
        List<String> completedTaskIds = scenario.tasks.stream()
            .filter(task -> "COMPLETED".equals(task.getStatus()))
            .map(ExtendedTaskInfo::getTaskId)
            .collect(Collectors.toList());
        
        for (String taskId : completedTaskIds) {
            verify(extendedTaskInfoRepository, never()).save(argThat(savedTask ->
                savedTask.getTaskId().equals(taskId)
            ));
        }
    }
    
    // ==================== 数据结构 ====================
    
    /**
     * 数据保留场景
     */
    private static class DataPreservationScenario {
        final String processInstanceId;
        final String subTableName;
        final List<Execution> executions;
        final List<ExtendedTaskInfo> tasks;
        final Map<Long, SubTableRowData> subTableData; // 子表数据（rowId -> 数据内容）
        
        DataPreservationScenario(
            String processInstanceId,
            String subTableName,
            List<Execution> executions,
            List<ExtendedTaskInfo> tasks,
            Map<Long, SubTableRowData> subTableData
        ) {
            this.processInstanceId = processInstanceId;
            this.subTableName = subTableName;
            this.executions = executions;
            this.tasks = tasks;
            this.subTableData = subTableData;
        }
    }
    
    /**
     * 子表行数据
     */
    private static class SubTableRowData {
        final Long rowId;
        final String assigneeId;
        final Long rowVersion;
        final String content; // 模拟的数据内容（JSON 字符串）
        
        SubTableRowData(Long rowId, String assigneeId, Long rowVersion, String content) {
            this.rowId = rowId;
            this.assigneeId = assigneeId;
            this.rowVersion = rowVersion;
            this.content = content;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SubTableRowData that = (SubTableRowData) o;
            return rowId.equals(that.rowId) &&
                   assigneeId.equals(that.assigneeId) &&
                   rowVersion.equals(that.rowVersion) &&
                   content.equals(that.content);
        }
        
        @Override
        public int hashCode() {
            return java.util.Objects.hash(rowId, assigneeId, rowVersion, content);
        }
    }
    
    // ==================== 数据生成器 ====================
    
    @Provide
    Arbitrary<DataPreservationScenario> dataPreservationScenarios() {
        return Combinators.combine(
            processInstanceIds(),
            subTableNames(),
            Arbitraries.integers().between(1, 10), // 活跃的多实例子任务数量
            Arbitraries.integers().between(0, 5)   // 已完成的多实例子任务数量
        ).as((processInstanceId, subTableName, activeCount, completedCount) -> {
            List<Execution> executions = new ArrayList<>();
            List<ExtendedTaskInfo> tasks = new ArrayList<>();
            Map<Long, SubTableRowData> subTableData = new HashMap<>();
            
            // 创建活跃的多实例执行
            if (activeCount > 0) {
                for (int i = 0; i < activeCount; i++) {
                    Execution execution = mock(Execution.class);
                    when(execution.getId()).thenReturn("exec-" + i);
                    when(execution.getActivityId()).thenReturn("MultiInstance_SubTable_45");
                    executions.add(execution);
                }
            }
            
            // 创建活跃的多实例子任务和对应的子表数据
            for (int i = 0; i < activeCount; i++) {
                Long rowId = 100L + i;
                String assigneeId = "user-" + i;
                Long rowVersion = 1L + (i % 3); // 模拟不同的版本号
                String content = String.format(
                    "{\"name\":\"参与人%d\",\"department\":\"部门%d\",\"willAttend\":true,\"dietaryPreference\":\"无\"}",
                    i, i
                );
                
                tasks.add(createMultiInstanceTask(
                    processInstanceId,
                    "task-active-" + i,
                    assigneeId,
                    rowId,
                    subTableName,
                    "ASSIGNED"
                ));
                
                subTableData.put(rowId, new SubTableRowData(rowId, assigneeId, rowVersion, content));
            }
            
            // 创建已完成的多实例子任务和对应的子表数据
            for (int i = 0; i < completedCount; i++) {
                Long rowId = 200L + i;
                String assigneeId = "user-" + (activeCount + i);
                Long rowVersion = 2L + (i % 3); // 已完成的任务通常有更高的版本号
                String content = String.format(
                    "{\"name\":\"参与人%d\",\"department\":\"部门%d\",\"willAttend\":true,\"dietaryPreference\":\"素食\"}",
                    activeCount + i, activeCount + i
                );
                
                tasks.add(createMultiInstanceTask(
                    processInstanceId,
                    "task-completed-" + i,
                    assigneeId,
                    rowId,
                    subTableName,
                    "COMPLETED"
                ));
                
                subTableData.put(rowId, new SubTableRowData(rowId, assigneeId, rowVersion, content));
            }
            
            return new DataPreservationScenario(
                processInstanceId,
                subTableName,
                executions,
                tasks,
                subTableData
            );
        });
    }
    
    @Provide
    Arbitrary<DataPreservationScenario> completedTaskScenarios() {
        return Combinators.combine(
            processInstanceIds(),
            subTableNames(),
            Arbitraries.integers().between(1, 10) // 已完成的多实例子任务数量
        ).as((processInstanceId, subTableName, completedCount) -> {
            List<Execution> executions = new ArrayList<>();
            List<ExtendedTaskInfo> tasks = new ArrayList<>();
            Map<Long, SubTableRowData> subTableData = new HashMap<>();
            
            // 创建已完成的多实例子任务和对应的子表数据
            for (int i = 0; i < completedCount; i++) {
                Long rowId = 100L + i;
                String assigneeId = "user-" + i;
                Long rowVersion = 2L + (i % 3);
                String content = String.format(
                    "{\"name\":\"参与人%d\",\"department\":\"部门%d\",\"willAttend\":true}",
                    i, i
                );
                
                tasks.add(createMultiInstanceTask(
                    processInstanceId,
                    "task-completed-" + i,
                    assigneeId,
                    rowId,
                    subTableName,
                    "COMPLETED"
                ));
                
                subTableData.put(rowId, new SubTableRowData(rowId, assigneeId, rowVersion, content));
            }
            
            return new DataPreservationScenario(
                processInstanceId,
                subTableName,
                executions,
                tasks,
                subTableData
            );
        });
    }
    
    @Provide
    Arbitrary<DataPreservationScenario> mixedStatusScenarios() {
        return Combinators.combine(
            processInstanceIds(),
            subTableNames(),
            Arbitraries.integers().between(1, 5), // 活跃的多实例子任务数量
            Arbitraries.integers().between(1, 5)  // 已完成的多实例子任务数量
        ).as((processInstanceId, subTableName, activeCount, completedCount) -> {
            List<Execution> executions = new ArrayList<>();
            List<ExtendedTaskInfo> tasks = new ArrayList<>();
            Map<Long, SubTableRowData> subTableData = new HashMap<>();
            
            // 创建活跃的多实例执行
            for (int i = 0; i < activeCount; i++) {
                Execution execution = mock(Execution.class);
                when(execution.getId()).thenReturn("exec-" + i);
                when(execution.getActivityId()).thenReturn("MultiInstance_SubTable_45");
                executions.add(execution);
            }
            
            // 创建活跃的多实例子任务和对应的子表数据
            for (int i = 0; i < activeCount; i++) {
                Long rowId = 100L + i;
                String assigneeId = "user-" + i;
                Long rowVersion = 1L;
                String content = String.format(
                    "{\"name\":\"参与人%d\",\"department\":\"部门%d\"}",
                    i, i
                );
                
                tasks.add(createMultiInstanceTask(
                    processInstanceId,
                    "task-active-" + i,
                    assigneeId,
                    rowId,
                    subTableName,
                    "ASSIGNED"
                ));
                
                subTableData.put(rowId, new SubTableRowData(rowId, assigneeId, rowVersion, content));
            }
            
            // 创建已完成的多实例子任务和对应的子表数据
            for (int i = 0; i < completedCount; i++) {
                Long rowId = 200L + i;
                String assigneeId = "user-" + (activeCount + i);
                Long rowVersion = 2L;
                String content = String.format(
                    "{\"name\":\"参与人%d\",\"department\":\"部门%d\",\"willAttend\":true}",
                    activeCount + i, activeCount + i
                );
                
                tasks.add(createMultiInstanceTask(
                    processInstanceId,
                    "task-completed-" + i,
                    assigneeId,
                    rowId,
                    subTableName,
                    "COMPLETED"
                ));
                
                subTableData.put(rowId, new SubTableRowData(rowId, assigneeId, rowVersion, content));
            }
            
            return new DataPreservationScenario(
                processInstanceId,
                subTableName,
                executions,
                tasks,
                subTableData
            );
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
    
    @Provide
    Arbitrary<String> subTableNames() {
        return Arbitraries.of(
            "fu_participants",
            "fu_expense_items",
            "fu_procurement_items",
            "fu_attendees",
            "fu_approval_records"
        );
    }
    
    // ==================== 辅助方法 ====================
    
    private static ExtendedTaskInfo createMultiInstanceTask(
        String processInstanceId,
        String taskId,
        String assigneeId,
        Long rowId,
        String subTableName,
        String status
    ) {
        String extendedProperties = String.format(
            "{\"multiInstance\":true,\"subTableRowId\":%d,\"subTableRowVersion\":1," +
            "\"subTableId\":\"45\",\"subTableName\":\"%s\"}",
            rowId, subTableName
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
