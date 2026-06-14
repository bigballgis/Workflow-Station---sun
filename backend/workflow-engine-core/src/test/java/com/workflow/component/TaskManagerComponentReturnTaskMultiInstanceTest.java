package com.workflow.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.request.TaskReturnRequest;
import com.workflow.dto.response.TaskAssignmentResult;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import com.workflow.exception.WorkflowValidationException;
import com.workflow.repository.ExtendedTaskInfoRepository;
import org.flowable.bpmn.model.*;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricActivityInstanceQuery;
import org.flowable.engine.runtime.ChangeActivityStateBuilder;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * returnTask() 多实例回退功能单元测试
 *
 * 说明：原本针对 TaskManagerComponent 的门面方法。TaskManagerComponent 已拆分为
 * 门面 + 协作类，returnTask 的实际逻辑现位于 {@link TaskCompletionService}
 * （MultiInstanceCanceller 仍由 TaskCompletionService 直接持有/调用）。
 * 判断回退目标是否在多实例子流程之前时，会用 {@link TaskMultiInstanceService#isMultiInstanceTask}
 * 过滤活跃 MI 子任务（@Lazy 调用）。因此本测试直接对 TaskCompletionService 注入被测 mock，
 * 并注入一个【真实】TaskMultiInstanceService，保证 MI 过滤逻辑真实执行、原断言依旧有效。
 *
 * 测试场景：
 * 1. 回退目标在多实例子流程之前，应调用 MultiInstanceCanceller 级联取消
 * 2. 回退目标不在多实例子流程之前，不应调用 MultiInstanceCanceller
 * 3. 无活跃多实例子任务时，不应调用 MultiInstanceCanceller
 *
 * **Validates: Requirements 9.2**
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskManagerComponentReturnTaskMultiInstanceTest {

    @Mock
    private TaskService taskService;

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private HistoryService historyService;

    @Mock
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;

    @Mock
    private MultiInstanceCanceller multiInstanceCanceller;

    // returnTask 主体现在落在 TaskCompletionService；@InjectMocks 注入其同名字段
    // （taskService/runtimeService/historyService/extendedTaskInfoRepository/multiInstanceCanceller）。
    @InjectMocks
    private TaskCompletionService taskCompletionService;

    // 回退前 MI 过滤（isMultiInstanceTask）真正执行处；用真实实例保证过滤逻辑真实运行。
    private TaskMultiInstanceService taskMultiInstanceService;

    private static final String TASK_ID = "task-current";
    private static final String USER_ID = "user-001";
    private static final String PROCESS_INSTANCE_ID = "process-001";
    private static final String PROCESS_DEFINITION_ID = "process-def-001";
    private static final String CURRENT_ACTIVITY_ID = "afterMultiInstanceTask";
    private static final String TARGET_ACTIVITY_ID = "beforeMultiInstanceTask";
    
    private ObjectMapper objectMapper = new ObjectMapper();
    
    @BeforeEach
    void setUp() {
        // isMultiInstanceTask 仅解析 extendedProperties JSON，无需注入字段；用真实实例保证过滤真实执行。
        taskMultiInstanceService = new TaskMultiInstanceService();
        ReflectionTestUtils.setField(taskCompletionService, "taskMultiInstanceService", taskMultiInstanceService);

        // 默认 mock 设置
        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId(anyString())).thenReturn(taskQuery);
    }
    
    /**
     * 测试场景 1：回退目标在多实例子流程之前，应调用 MultiInstanceCanceller 级联取消
     * 
     * 流程结构：
     * beforeMultiInstanceTask -> MultiInstance_SubProcess -> afterMultiInstanceTask (当前)
     * 
     * 回退到 beforeMultiInstanceTask，应级联取消多实例子任务
     */
    @Test
    void testReturnTask_TargetBeforeMultiInstance_ShouldCancelMultiInstanceTasks() throws Exception {
        // Given: 设置当前任务
        Task currentTask = createMockTask(TASK_ID, PROCESS_INSTANCE_ID, PROCESS_DEFINITION_ID, CURRENT_ACTIVITY_ID);
        TaskQuery taskQuery = taskService.createTaskQuery();
        when(taskQuery.singleResult()).thenReturn(currentTask);
        
        // 设置历史活动（验证目标节点存在）
        HistoricActivityInstance historicActivity = mock(HistoricActivityInstance.class);
        when(historicActivity.getActivityId()).thenReturn(TARGET_ACTIVITY_ID);
        // 设置目标活动的完成时间（早于多实例子任务创建时间）
        java.util.Date targetEndDate = java.util.Date.from(
            LocalDateTime.now().minusHours(2).atZone(java.time.ZoneId.systemDefault()).toInstant()
        );
        when(historicActivity.getEndTime()).thenReturn(targetEndDate);
        
        HistoricActivityInstanceQuery historyQuery = mock(HistoricActivityInstanceQuery.class);
        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(historyQuery);
        when(historyQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(historyQuery);
        when(historyQuery.activityId(TARGET_ACTIVITY_ID)).thenReturn(historyQuery);
        when(historyQuery.finished()).thenReturn(historyQuery);
        when(historyQuery.orderByHistoricActivityInstanceEndTime()).thenReturn(historyQuery);
        when(historyQuery.desc()).thenReturn(historyQuery);
        when(historyQuery.list()).thenReturn(Collections.singletonList(historicActivity));
        
        // 设置活跃的多实例子任务
        List<ExtendedTaskInfo> activeMultiInstanceTasks = createActiveMultiInstanceTasks();
        when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(PROCESS_INSTANCE_ID))
            .thenReturn(activeMultiInstanceTasks);
        
        // 设置回退操作
        ChangeActivityStateBuilder changeBuilder = mock(ChangeActivityStateBuilder.class);
        when(runtimeService.createChangeActivityStateBuilder()).thenReturn(changeBuilder);
        when(changeBuilder.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(changeBuilder);
        when(changeBuilder.moveActivityIdTo(CURRENT_ACTIVITY_ID, TARGET_ACTIVITY_ID)).thenReturn(changeBuilder);
        
        // 设置扩展任务信息（使用 lenient 避免 unnecessary stubbing 错误）
        ExtendedTaskInfo extendedTaskInfo = createExtendedTaskInfo(TASK_ID, false);
        lenient().when(extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(TASK_ID))
            .thenReturn(Optional.of(extendedTaskInfo));
        
        // When: 回退任务
        TaskReturnRequest request = new TaskReturnRequest();
        request.setUserId(USER_ID);
        request.setTargetActivityId(TARGET_ACTIVITY_ID);
        request.setReason("需要重新审批");
        
        TaskAssignmentResult result = taskCompletionService.returnTask(TASK_ID, request);
        
        // Then: 验证 MultiInstanceCanceller 被调用
        verify(multiInstanceCanceller, times(1)).cancelMultiInstanceTasks(PROCESS_INSTANCE_ID);
        
        // 验证回退操作执行
        verify(changeBuilder, times(1)).changeState();
        
        // 验证结果
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTaskId()).isEqualTo(TASK_ID);
    }
    
    /**
     * 测试场景 2：回退目标不在多实例子流程之前，不应调用 MultiInstanceCanceller
     * 
     * 流程结构：
     * beforeTask -> MultiInstance_SubProcess (包含 MI_UserTask_45) -> afterTask1 -> afterTask2 (当前)
     * 
     * 回退到 afterTask1（在多实例之后），不应级联取消
     */
    @Test
    void testReturnTask_TargetNotBeforeMultiInstance_ShouldNotCancelMultiInstanceTasks() throws Exception {
        // Given: 设置当前任务
        String currentActivityId = "afterTask2";
        String targetActivityId = "afterTask1";
        
        Task currentTask = createMockTask(TASK_ID, PROCESS_INSTANCE_ID, PROCESS_DEFINITION_ID, currentActivityId);
        TaskQuery taskQuery = taskService.createTaskQuery();
        when(taskQuery.singleResult()).thenReturn(currentTask);
        
        // 设置历史活动
        HistoricActivityInstance historicActivity = mock(HistoricActivityInstance.class);
        when(historicActivity.getActivityId()).thenReturn(targetActivityId);
        // 设置目标活动的完成时间（晚于多实例子任务创建时间）
        java.util.Date targetEndDate = java.util.Date.from(
            LocalDateTime.now().plusHours(1).atZone(java.time.ZoneId.systemDefault()).toInstant()
        );
        when(historicActivity.getEndTime()).thenReturn(targetEndDate);
        
        HistoricActivityInstanceQuery historyQuery = mock(HistoricActivityInstanceQuery.class);
        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(historyQuery);
        when(historyQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(historyQuery);
        when(historyQuery.activityId(targetActivityId)).thenReturn(historyQuery);
        when(historyQuery.finished()).thenReturn(historyQuery);
        when(historyQuery.orderByHistoricActivityInstanceEndTime()).thenReturn(historyQuery);
        when(historyQuery.desc()).thenReturn(historyQuery);
        when(historyQuery.list()).thenReturn(Collections.singletonList(historicActivity));
        
        // 设置活跃的多实例子任务
        List<ExtendedTaskInfo> activeMultiInstanceTasks = createActiveMultiInstanceTasks();
        when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(PROCESS_INSTANCE_ID))
            .thenReturn(activeMultiInstanceTasks);
        
        // 设置回退操作
        ChangeActivityStateBuilder changeBuilder = mock(ChangeActivityStateBuilder.class);
        when(runtimeService.createChangeActivityStateBuilder()).thenReturn(changeBuilder);
        when(changeBuilder.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(changeBuilder);
        when(changeBuilder.moveActivityIdTo(currentActivityId, targetActivityId)).thenReturn(changeBuilder);
        
        // 设置扩展任务信息（使用 lenient 避免 unnecessary stubbing 错误）
        ExtendedTaskInfo extendedTaskInfo = createExtendedTaskInfo(TASK_ID, false);
        lenient().when(extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(TASK_ID))
            .thenReturn(Optional.of(extendedTaskInfo));
        
        // When: 回退任务
        TaskReturnRequest request = new TaskReturnRequest();
        request.setUserId(USER_ID);
        request.setTargetActivityId(targetActivityId);
        request.setReason("需要重新审批");
        
        TaskAssignmentResult result = taskCompletionService.returnTask(TASK_ID, request);
        
        // Then: 验证 MultiInstanceCanceller 未被调用
        verify(multiInstanceCanceller, never()).cancelMultiInstanceTasks(any());
        
        // 验证回退操作执行
        verify(changeBuilder, times(1)).changeState();
        
        // 验证结果
        assertThat(result.isSuccess()).isTrue();
    }
    
    /**
     * 测试场景 3：无活跃多实例子任务时，不应调用 MultiInstanceCanceller
     */
    @Test
    void testReturnTask_NoActiveMultiInstanceTasks_ShouldNotCancelMultiInstanceTasks() throws Exception {
        // Given: 设置当前任务
        Task currentTask = createMockTask(TASK_ID, PROCESS_INSTANCE_ID, PROCESS_DEFINITION_ID, CURRENT_ACTIVITY_ID);
        TaskQuery taskQuery = taskService.createTaskQuery();
        when(taskQuery.singleResult()).thenReturn(currentTask);
        
        // 设置历史活动
        HistoricActivityInstance historicActivity = mock(HistoricActivityInstance.class);
        when(historicActivity.getActivityId()).thenReturn(TARGET_ACTIVITY_ID);
        
        HistoricActivityInstanceQuery historyQuery = mock(HistoricActivityInstanceQuery.class);
        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(historyQuery);
        when(historyQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(historyQuery);
        when(historyQuery.activityId(TARGET_ACTIVITY_ID)).thenReturn(historyQuery);
        when(historyQuery.finished()).thenReturn(historyQuery);
        when(historyQuery.orderByHistoricActivityInstanceEndTime()).thenReturn(historyQuery);
        when(historyQuery.desc()).thenReturn(historyQuery);
        when(historyQuery.list()).thenReturn(Collections.singletonList(historicActivity));
        
        // 设置无活跃的多实例子任务（所有任务都已完成）
        when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(PROCESS_INSTANCE_ID))
            .thenReturn(Collections.emptyList());
        
        // 设置回退操作
        ChangeActivityStateBuilder changeBuilder = mock(ChangeActivityStateBuilder.class);
        when(runtimeService.createChangeActivityStateBuilder()).thenReturn(changeBuilder);
        when(changeBuilder.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(changeBuilder);
        when(changeBuilder.moveActivityIdTo(CURRENT_ACTIVITY_ID, TARGET_ACTIVITY_ID)).thenReturn(changeBuilder);
        
        // 设置扩展任务信息（使用 lenient 避免 unnecessary stubbing 错误）
        ExtendedTaskInfo extendedTaskInfo = createExtendedTaskInfo(TASK_ID, false);
        lenient().when(extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(TASK_ID))
            .thenReturn(Optional.of(extendedTaskInfo));
        
        // When: 回退任务
        TaskReturnRequest request = new TaskReturnRequest();
        request.setUserId(USER_ID);
        request.setTargetActivityId(TARGET_ACTIVITY_ID);
        request.setReason("需要重新审批");
        
        TaskAssignmentResult result = taskCompletionService.returnTask(TASK_ID, request);
        
        // Then: 验证 MultiInstanceCanceller 未被调用
        verify(multiInstanceCanceller, never()).cancelMultiInstanceTasks(any());
        
        // 验证回退操作执行
        verify(changeBuilder, times(1)).changeState();
        
        // 验证结果
        assertThat(result.isSuccess()).isTrue();
    }
    
    /**
     * 测试场景 4：回退目标节点不存在，应抛出验证异常
     */
    @Test
    void testReturnTask_TargetActivityNotFound_ShouldThrowException() {
        // Given: 设置当前任务
        Task currentTask = createMockTask(TASK_ID, PROCESS_INSTANCE_ID, PROCESS_DEFINITION_ID, CURRENT_ACTIVITY_ID);
        TaskQuery taskQuery = taskService.createTaskQuery();
        when(taskQuery.singleResult()).thenReturn(currentTask);
        
        // 设置历史活动查询返回空列表（目标节点不存在）
        HistoricActivityInstanceQuery historyQuery = mock(HistoricActivityInstanceQuery.class);
        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(historyQuery);
        when(historyQuery.processInstanceId(PROCESS_INSTANCE_ID)).thenReturn(historyQuery);
        when(historyQuery.activityId(TARGET_ACTIVITY_ID)).thenReturn(historyQuery);
        when(historyQuery.finished()).thenReturn(historyQuery);
        when(historyQuery.orderByHistoricActivityInstanceEndTime()).thenReturn(historyQuery);
        when(historyQuery.desc()).thenReturn(historyQuery);
        when(historyQuery.list()).thenReturn(Collections.emptyList());
        
        // When & Then: 回退任务应抛出验证异常
        TaskReturnRequest request = new TaskReturnRequest();
        request.setUserId(USER_ID);
        request.setTargetActivityId(TARGET_ACTIVITY_ID);
        request.setReason("需要重新审批");
        
        assertThatThrownBy(() -> taskCompletionService.returnTask(TASK_ID, request))
            .isInstanceOf(WorkflowValidationException.class)
            .hasMessageContaining("Target activity is not a valid historic activity");
        
        // 验证 MultiInstanceCanceller 未被调用
        verify(multiInstanceCanceller, never()).cancelMultiInstanceTasks(any());
    }
    
    // ==================== 辅助方法 ====================
    
    private Task createMockTask(String taskId, String processInstanceId, 
                               String processDefinitionId, String taskDefinitionKey) {
        Task task = mock(Task.class, withSettings().lenient());
        when(task.getId()).thenReturn(taskId);
        when(task.getProcessInstanceId()).thenReturn(processInstanceId);
        when(task.getProcessDefinitionId()).thenReturn(processDefinitionId);
        when(task.getTaskDefinitionKey()).thenReturn(taskDefinitionKey);
        return task;
    }
    
    private ExtendedTaskInfo createExtendedTaskInfo(String taskId, boolean isMultiInstance) throws Exception {
        Map<String, Object> extendedProperties = new HashMap<>();
        if (isMultiInstance) {
            extendedProperties.put("multiInstance", true);
            extendedProperties.put("subTableRowId", 101L);
            extendedProperties.put("subTableName", "fu_participants");
        }
        
        String extendedPropertiesJson = objectMapper.writeValueAsString(extendedProperties);
        
        return ExtendedTaskInfo.builder()
            .id(1L)
            .taskId(taskId)
            .processInstanceId(PROCESS_INSTANCE_ID)
            .processDefinitionId(PROCESS_DEFINITION_ID)
            .taskDefinitionKey(CURRENT_ACTIVITY_ID)
            .taskName("测试任务")
            .assignmentType(AssignmentType.USER)
            .assignmentTarget(USER_ID)
            .status("ASSIGNED")
            .createdTime(LocalDateTime.now())
            .extendedProperties(extendedPropertiesJson)
            .build();
    }
    
    /**
     * 创建活跃的多实例子任务列表
     */
    private List<ExtendedTaskInfo> createActiveMultiInstanceTasks() throws Exception {
        List<ExtendedTaskInfo> tasks = new ArrayList<>();
        
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> extendedProperties = new HashMap<>();
            extendedProperties.put("multiInstance", true);
            extendedProperties.put("subTableRowId", 100L + i);
            extendedProperties.put("subTableName", "fu_participants");
            
            String extendedPropertiesJson = objectMapper.writeValueAsString(extendedProperties);
            
            ExtendedTaskInfo task = ExtendedTaskInfo.builder()
                .id((long) i)
                .taskId("mi-task-00" + i)
                .processInstanceId(PROCESS_INSTANCE_ID)
                .processDefinitionId(PROCESS_DEFINITION_ID)
                .taskDefinitionKey("MI_UserTask_45")
                .taskName("补充个人信息")
                .assignmentType(AssignmentType.USER)
                .assignmentTarget("user-00" + i)
                .status("ASSIGNED")
                .createdTime(LocalDateTime.now())
                .extendedProperties(extendedPropertiesJson)
                .build();
            
            tasks.add(task);
        }
        
        return tasks;
    }
}
