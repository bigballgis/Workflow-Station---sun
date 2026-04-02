package com.workflow.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.MultiInstanceCancelResult;
import com.workflow.entity.AuditLog;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import com.workflow.repository.AuditLogRepository;
import com.workflow.repository.ExtendedTaskInfoRepository;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ExecutionQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * MultiInstanceCanceller 单元测试
 * 
 * 测试多实例子任务取消的各种场景
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MultiInstanceCanceller 单元测试")
class MultiInstanceCancellerTest {
    
    @Mock
    private RuntimeService runtimeService;
    
    @Mock
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;
    
    @Mock
    private AuditLogRepository auditLogRepository;
    
    @Mock
    private ExecutionQuery executionQuery;
    
    @InjectMocks
    private MultiInstanceCanceller canceller;
    
    private static final String PROCESS_INSTANCE_ID = "proc-inst-001";
    private static final String TASK_ID_1 = "task-001";
    private static final String TASK_ID_2 = "task-002";
    private static final String TASK_ID_3 = "task-003";
    
    @BeforeEach
    void setUp() {
        // 设置 RuntimeService mock
        when(runtimeService.createExecutionQuery()).thenReturn(executionQuery);
        when(executionQuery.processInstanceId(anyString())).thenReturn(executionQuery);
    }
    
    @Test
    @DisplayName("示例：取消 3 个活跃的多实例子任务")
    void testCancelMultipleActiveTasks() {
        // Given: 3 个活跃的多实例子任务
        List<Execution> executions = createMockExecutions(3);
        when(executionQuery.list()).thenReturn(executions);
        
        List<ExtendedTaskInfo> tasks = createMockExtendedTaskInfos(3);
        when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(PROCESS_INSTANCE_ID))
            .thenReturn(tasks);
        
        when(extendedTaskInfoRepository.save(any(ExtendedTaskInfo.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: 执行取消操作
        MultiInstanceCancelResult result = canceller.cancelMultiInstanceTasks(PROCESS_INSTANCE_ID);
        
        // Then: 验证取消结果
        assertNotNull(result);
        assertEquals(3, result.getCancelledCount());
        assertEquals(0, result.getFailedCount());
        assertEquals(3, result.getCancelledTasks().size());
        
        // 验证每个任务的状态都被更新为 CANCELLED
        verify(extendedTaskInfoRepository, times(3)).save(argThat(task -> 
            "CANCELLED".equals(task.getStatus())
        ));
        
        // 验证审计日志被记录
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
        
        // 验证取消详情
        List<MultiInstanceCancelResult.CancelledTaskDetail> cancelledTasks = result.getCancelledTasks();
        assertEquals(TASK_ID_1, cancelledTasks.get(0).getTaskId());
        assertEquals("user-001", cancelledTasks.get(0).getAssigneeId());
        assertEquals("ASSIGNED", cancelledTasks.get(0).getPreviousStatus());
        assertEquals(101L, cancelledTasks.get(0).getSubTableRowId());
        assertEquals("fu_participants", cancelledTasks.get(0).getSubTableName());
    }
    
    @Test
    @DisplayName("边界：取消 5 个子任务中的 3 个活跃任务")
    void testCancelThreeActiveOutOfFiveTasks() {
        // Given: 5 个子任务，其中 3 个活跃（ASSIGNED），2 个已完成（COMPLETED）
        List<Execution> executions = createMockExecutions(5);
        when(executionQuery.list()).thenReturn(executions);
        
        List<ExtendedTaskInfo> tasks = new ArrayList<>();
        // 前 3 个任务是活跃的
        tasks.add(createMockExtendedTaskInfo(TASK_ID_1, "user-001", 101L, "ASSIGNED"));
        tasks.add(createMockExtendedTaskInfo(TASK_ID_2, "user-002", 102L, "ASSIGNED"));
        tasks.add(createMockExtendedTaskInfo(TASK_ID_3, "user-003", 103L, "ASSIGNED"));
        // 后 2 个任务已完成
        tasks.add(createCompletedTask("task-004", "user-004", 104L));
        tasks.add(createCompletedTask("task-005", "user-005", 105L));
        
        when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(PROCESS_INSTANCE_ID))
            .thenReturn(tasks);
        
        when(extendedTaskInfoRepository.save(any(ExtendedTaskInfo.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: 执行取消操作
        MultiInstanceCancelResult result = canceller.cancelMultiInstanceTasks(PROCESS_INSTANCE_ID);
        
        // Then: 验证只取消了 3 个活跃任务
        assertNotNull(result);
        assertEquals(3, result.getCancelledCount());
        assertEquals(0, result.getFailedCount());
        assertEquals(3, result.getCancelledTasks().size());
        
        // 验证只有活跃任务的状态被更新为 CANCELLED
        verify(extendedTaskInfoRepository, times(3)).save(argThat(task -> 
            "CANCELLED".equals(task.getStatus())
        ));
        
        // 验证取消的是前 3 个任务
        List<MultiInstanceCancelResult.CancelledTaskDetail> cancelledTasks = result.getCancelledTasks();
        assertEquals(TASK_ID_1, cancelledTasks.get(0).getTaskId());
        assertEquals(TASK_ID_2, cancelledTasks.get(1).getTaskId());
        assertEquals(TASK_ID_3, cancelledTasks.get(2).getTaskId());
        
        // 验证所有取消的任务之前都是 ASSIGNED 状态
        assertTrue(cancelledTasks.stream().allMatch(task -> 
            "ASSIGNED".equals(task.getPreviousStatus())
        ));
        
        // 验证审计日志被记录
        ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(auditLogCaptor.capture());
        
        AuditLog auditLog = auditLogCaptor.getValue();
        assertEquals("SUCCESS", auditLog.getOperationResult());
        assertTrue(auditLog.getOperationDescription().contains("共取消 3 个任务"));
    }
    
    @Test
    @DisplayName("边界：无活跃子任务时静默跳过")
    void testNoActiveTasksSilentSkip() {
        // Given: 没有活跃的多实例执行
        when(executionQuery.list()).thenReturn(new ArrayList<>());
        
        // When: 执行取消操作
        MultiInstanceCancelResult result = canceller.cancelMultiInstanceTasks(PROCESS_INSTANCE_ID);
        
        // Then: 验证静默跳过
        assertNotNull(result);
        assertEquals(0, result.getCancelledCount());
        assertEquals(0, result.getFailedCount());
        assertTrue(result.getCancelledTasks().isEmpty());
        
        // 验证没有更新任何任务
        verify(extendedTaskInfoRepository, never()).save(any(ExtendedTaskInfo.class));
        
        // 验证没有记录审计日志
        verify(auditLogRepository, never()).save(any(AuditLog.class));
    }
    
    @Test
    @DisplayName("边界：只有已完成的任务时不取消")
    void testOnlyCompletedTasksNotCancelled() {
        // Given: 有多实例执行，但所有任务都已完成
        List<Execution> executions = createMockExecutions(2);
        when(executionQuery.list()).thenReturn(executions);
        
        List<ExtendedTaskInfo> tasks = new ArrayList<>();
        tasks.add(createCompletedTask(TASK_ID_1, "user-001", 101L));
        tasks.add(createCompletedTask(TASK_ID_2, "user-002", 102L));
        
        when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(PROCESS_INSTANCE_ID))
            .thenReturn(tasks);
        
        // When: 执行取消操作
        MultiInstanceCancelResult result = canceller.cancelMultiInstanceTasks(PROCESS_INSTANCE_ID);
        
        // Then: 验证没有任务被取消
        assertNotNull(result);
        assertEquals(0, result.getCancelledCount());
        assertEquals(0, result.getFailedCount());
        assertTrue(result.getCancelledTasks().isEmpty());
        
        // 验证没有更新任何任务
        verify(extendedTaskInfoRepository, never()).save(any(ExtendedTaskInfo.class));
    }
    
    @Test
    @DisplayName("边界：部分更新失败时继续处理其他任务")
    void testPartialFailureContinuesProcessing() {
        // Given: 3 个活跃的多实例子任务，其中第 2 个更新失败
        List<Execution> executions = createMockExecutions(3);
        when(executionQuery.list()).thenReturn(executions);
        
        List<ExtendedTaskInfo> tasks = createMockExtendedTaskInfos(3);
        when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(PROCESS_INSTANCE_ID))
            .thenReturn(tasks);
        
        // 第 1 个和第 3 个任务保存成功，第 2 个任务保存失败
        when(extendedTaskInfoRepository.save(any(ExtendedTaskInfo.class)))
            .thenAnswer(invocation -> {
                ExtendedTaskInfo task = invocation.getArgument(0);
                if (TASK_ID_2.equals(task.getTaskId())) {
                    throw new RuntimeException("Database error");
                }
                return task;
            });
        
        // When: 执行取消操作
        MultiInstanceCancelResult result = canceller.cancelMultiInstanceTasks(PROCESS_INSTANCE_ID);
        
        // Then: 验证部分成功
        assertNotNull(result);
        assertEquals(2, result.getCancelledCount()); // 第 1 个和第 3 个成功
        assertEquals(1, result.getFailedCount()); // 第 2 个失败
        assertEquals(2, result.getCancelledTasks().size());
        
        // 验证审计日志被记录（包含失败信息）
        ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(auditLogCaptor.capture());
        
        AuditLog auditLog = auditLogCaptor.getValue();
        assertEquals("PARTIAL", auditLog.getOperationResult());
        assertTrue(auditLog.getOperationDescription().contains("失败 1 个"));
    }
    
    @Test
    @DisplayName("验证：审计日志包含完整的取消信息")
    void testAuditLogContainsCompleteInformation() {
        // Given: 2 个活跃的多实例子任务
        List<Execution> executions = createMockExecutions(2);
        when(executionQuery.list()).thenReturn(executions);
        
        List<ExtendedTaskInfo> tasks = createMockExtendedTaskInfos(2);
        when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(PROCESS_INSTANCE_ID))
            .thenReturn(tasks);
        
        when(extendedTaskInfoRepository.save(any(ExtendedTaskInfo.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: 执行取消操作
        canceller.cancelMultiInstanceTasks(PROCESS_INSTANCE_ID);
        
        // Then: 验证审计日志内容
        ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(auditLogCaptor.capture());
        
        AuditLog auditLog = auditLogCaptor.getValue();
        assertNotNull(auditLog.getId());
        assertEquals("SYSTEM", auditLog.getUserId());
        assertEquals("CANCEL", auditLog.getOperationType());
        assertEquals("MULTI_INSTANCE_TASKS", auditLog.getResourceType());
        assertEquals(PROCESS_INSTANCE_ID, auditLog.getResourceId());
        assertEquals("SUCCESS", auditLog.getOperationResult());
        assertNotNull(auditLog.getTimestamp());
        assertNotNull(auditLog.getContextData());
        
        // 验证 contextData 包含取消详情
        assertTrue(auditLog.getContextData().contains("cancelledCount"));
        assertTrue(auditLog.getContextData().contains("cancelledTasks"));
    }
    
    @Test
    @DisplayName("边界：extendedProperties 为空时跳过该任务")
    void testSkipTaskWithEmptyExtendedProperties() {
        // Given: 有多实例执行，但任务的 extendedProperties 为空
        List<Execution> executions = createMockExecutions(2);
        when(executionQuery.list()).thenReturn(executions);
        
        List<ExtendedTaskInfo> tasks = new ArrayList<>();
        tasks.add(createTaskWithEmptyProperties(TASK_ID_1, "user-001"));
        tasks.add(createMockExtendedTaskInfo(TASK_ID_2, "user-002", 102L, "ASSIGNED"));
        
        when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(PROCESS_INSTANCE_ID))
            .thenReturn(tasks);
        
        when(extendedTaskInfoRepository.save(any(ExtendedTaskInfo.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: 执行取消操作
        MultiInstanceCancelResult result = canceller.cancelMultiInstanceTasks(PROCESS_INSTANCE_ID);
        
        // Then: 验证只取消了第 2 个任务
        assertNotNull(result);
        assertEquals(1, result.getCancelledCount());
        assertEquals(0, result.getFailedCount());
        assertEquals(1, result.getCancelledTasks().size());
        assertEquals(TASK_ID_2, result.getCancelledTasks().get(0).getTaskId());
    }
    
    @Test
    @DisplayName("边界：extendedProperties 中 multiInstance 为 false 时跳过")
    void testSkipTaskWithMultiInstanceFalse() {
        // Given: 有多实例执行，但任务的 multiInstance 标记为 false
        List<Execution> executions = createMockExecutions(2);
        when(executionQuery.list()).thenReturn(executions);
        
        List<ExtendedTaskInfo> tasks = new ArrayList<>();
        tasks.add(createTaskWithMultiInstanceFalse(TASK_ID_1, "user-001"));
        tasks.add(createMockExtendedTaskInfo(TASK_ID_2, "user-002", 102L, "ASSIGNED"));
        
        when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(PROCESS_INSTANCE_ID))
            .thenReturn(tasks);
        
        when(extendedTaskInfoRepository.save(any(ExtendedTaskInfo.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: 执行取消操作
        MultiInstanceCancelResult result = canceller.cancelMultiInstanceTasks(PROCESS_INSTANCE_ID);
        
        // Then: 验证只取消了第 2 个任务
        assertNotNull(result);
        assertEquals(1, result.getCancelledCount());
        assertEquals(0, result.getFailedCount());
        assertEquals(1, result.getCancelledTasks().size());
        assertEquals(TASK_ID_2, result.getCancelledTasks().get(0).getTaskId());
    }
    
    // ==================== Helper Methods ====================
    
    private List<Execution> createMockExecutions(int count) {
        List<Execution> executions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Execution execution = mock(Execution.class);
            lenient().when(execution.getId()).thenReturn("exec-" + (i + 1));
            lenient().when(execution.getActivityId()).thenReturn("MultiInstance_SubTable_45");
            executions.add(execution);
        }
        return executions;
    }
    
    private List<ExtendedTaskInfo> createMockExtendedTaskInfos(int count) {
        List<ExtendedTaskInfo> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String taskId = "task-" + String.format("%03d", i + 1);
            String assigneeId = "user-" + String.format("%03d", i + 1);
            Long rowId = 101L + i;
            tasks.add(createMockExtendedTaskInfo(taskId, assigneeId, rowId, "ASSIGNED"));
        }
        return tasks;
    }
    
    private ExtendedTaskInfo createMockExtendedTaskInfo(String taskId, String assigneeId, 
                                                        Long rowId, String status) {
        String extendedProperties = String.format(
            "{\"multiInstance\":true,\"subTableRowId\":%d,\"subTableRowVersion\":1," +
            "\"subTableId\":\"45\",\"subTableName\":\"fu_participants\"}",
            rowId
        );
        
        return ExtendedTaskInfo.builder()
            .id((long) taskId.hashCode())
            .taskId(taskId)
            .processInstanceId(PROCESS_INSTANCE_ID)
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
    
    private ExtendedTaskInfo createCompletedTask(String taskId, String assigneeId, Long rowId) {
        return createMockExtendedTaskInfo(taskId, assigneeId, rowId, "COMPLETED");
    }
    
    private ExtendedTaskInfo createTaskWithEmptyProperties(String taskId, String assigneeId) {
        return ExtendedTaskInfo.builder()
            .id((long) taskId.hashCode())
            .taskId(taskId)
            .processInstanceId(PROCESS_INSTANCE_ID)
            .processDefinitionId("proc-def-001")
            .assignmentType(AssignmentType.USER)
            .assignmentTarget(assigneeId)
            .status("ASSIGNED")
            .createdTime(LocalDateTime.now())
            .extendedProperties("") // 空字符串
            .isDeleted(false)
            .build();
    }
    
    private ExtendedTaskInfo createTaskWithMultiInstanceFalse(String taskId, String assigneeId) {
        String extendedProperties = "{\"multiInstance\":false,\"subTableRowId\":101}";
        
        return ExtendedTaskInfo.builder()
            .id((long) taskId.hashCode())
            .taskId(taskId)
            .processInstanceId(PROCESS_INSTANCE_ID)
            .processDefinitionId("proc-def-001")
            .assignmentType(AssignmentType.USER)
            .assignmentTarget(assigneeId)
            .status("ASSIGNED")
            .createdTime(LocalDateTime.now())
            .extendedProperties(extendedProperties)
            .isDeleted(false)
            .build();
    }
}
