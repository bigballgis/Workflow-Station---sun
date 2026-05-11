package com.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.response.ApiResponse;
import com.workflow.dto.response.MultiInstanceStatusResponse;
import com.workflow.dto.response.SubTableDataResponse;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import com.workflow.repository.ExtendedTaskInfoRepository;
import com.workflow.component.BpmnActionParser;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ExecutionQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * MultiInstanceStatusController 单元测试
 * 
 * **Validates: Requirements 7.1, 7.2**
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MultiInstanceStatusController 单元测试")
class MultiInstanceStatusControllerTest {

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private TaskService taskService;

    @Mock
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;

    @Mock
    private ObjectMapper objectMapper;
    
    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private BpmnActionParser bpmnActionParser;

    @InjectMocks
    private MultiInstanceStatusController controller;

    @Mock
    private ExecutionQuery executionQuery;

    @Mock
    private Execution multiInstanceExecution;
    
    @Mock
    private TaskQuery taskQuery;
    
    @Mock
    private Task task;

    private String processInstanceId;
    private List<ExtendedTaskInfo> multiInstanceTasks;

    @BeforeEach
    void setUp() {
        processInstanceId = "process-instance-001";
        multiInstanceTasks = new ArrayList<>();
        lenient().when(bpmnActionParser.getMultiInstanceSubProcessSubTableName(anyString(), anyString()))
                .thenReturn("fu_participants");
        lenient().when(bpmnActionParser.getUserTaskExtensionPropertyValue(anyString(), anyString(), anyString()))
                .thenReturn(null);
        lenient().when(bpmnActionParser.getMultiInstanceSubProcessExtensionPropertyValue(anyString(), anyString(), anyString()))
                .thenReturn(null);
        lenient().when(jdbcTemplate.query(
                argThat((String sql) -> sql != null && sql.contains("PRIMARY KEY")),
                any(RowMapper.class),
                anyString()))
                .thenReturn(Collections.singletonList("id"));
        lenient().when(taskService.createTaskQuery()).thenReturn(taskQuery);
        lenient().when(taskQuery.processInstanceId(anyString())).thenReturn(taskQuery);
        lenient().when(taskQuery.list()).thenReturn(Collections.emptyList());
    }

    @Nested
    @DisplayName("查询多实例执行状态")
    class GetStatusTests {

        @Test
        @DisplayName("应该成功返回多实例执行状态 - 包含总实例数、已完成数、进行中数")
        void shouldReturnMultiInstanceStatus() throws Exception {
            // Given: 准备多实例执行数据
            when(runtimeService.createExecutionQuery()).thenReturn(executionQuery);
            when(executionQuery.processInstanceId(processInstanceId)).thenReturn(executionQuery);
            
            List<Execution> executions = Arrays.asList(multiInstanceExecution);
            when(executionQuery.list()).thenReturn(executions);
            
            when(multiInstanceExecution.getId()).thenReturn("execution-001");
            
            Map<String, Object> miVariables = new HashMap<>();
            miVariables.put("nrOfInstances", 5);
            miVariables.put("nrOfCompletedInstances", 3);
            miVariables.put("nrOfActiveInstances", 2);
            when(runtimeService.getVariables("execution-001")).thenReturn(miVariables);
            
            when(multiInstanceExecution.getActivityId()).thenReturn("MultiInstance_SubTable_45");
            
            // 准备扩展任务信息
            ExtendedTaskInfo task1 = createMultiInstanceTask("task-001", "user-001", "COMPLETED", 101L);
            ExtendedTaskInfo task2 = createMultiInstanceTask("task-002", "user-002", "COMPLETED", 102L);
            ExtendedTaskInfo task3 = createMultiInstanceTask("task-003", "user-003", "COMPLETED", 103L);
            ExtendedTaskInfo task4 = createMultiInstanceTask("task-004", "user-004", "ASSIGNED", 104L);
            ExtendedTaskInfo task5 = createMultiInstanceTask("task-005", "user-005", "ASSIGNED", 105L);
            
            multiInstanceTasks = Arrays.asList(task1, task2, task3, task4, task5);
            when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(processInstanceId))
                    .thenReturn(multiInstanceTasks);
            
            // Mock ObjectMapper 解析扩展属性
            when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                    .thenAnswer(invocation -> {
                        Map<String, Object> props = new HashMap<>();
                        props.put("multiInstance", true);
                        props.put("subTableName", "fu_participants");
                        props.put("subTableRowId", 101);
                        return props;
                    });
            
            // When: 调用查询接口
            ResponseEntity<ApiResponse<MultiInstanceStatusResponse>> response = controller.getStatus(processInstanceId);
            
            // Then: 验证响应
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            
            MultiInstanceStatusResponse data = response.getBody().getData();
            assertThat(data).isNotNull();
            assertThat(data.getProcessInstanceId()).isEqualTo(processInstanceId);
            assertThat(data.getTotalInstances()).isEqualTo(5);
            assertThat(data.getCompletedInstances()).isEqualTo(3);
            assertThat(data.getActiveInstances()).isEqualTo(2);
            assertThat(data.getStatus()).isEqualTo("ACTIVE");
            assertThat(data.getTasks()).hasSize(5);
        }

        @Test
        @DisplayName("应该返回错误 - 当流程实例中未找到多实例执行时")
        void shouldReturnError_WhenMultiInstanceNotFound() {
            // Given: 流程实例中没有多实例执行
            when(runtimeService.createExecutionQuery()).thenReturn(executionQuery);
            when(executionQuery.processInstanceId(processInstanceId)).thenReturn(executionQuery);
            when(executionQuery.list()).thenReturn(Collections.emptyList());
            
            // When: 调用查询接口
            ResponseEntity<ApiResponse<MultiInstanceStatusResponse>> response = controller.getStatus(processInstanceId);
            
            // Then: 验证返回错误
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getCode()).isEqualTo("MULTI_INSTANCE_NOT_FOUND");
        }

        @Test
        @DisplayName("应该正确聚合子任务信息 - 包含处理人和状态")
        void shouldAggregateSubTaskInfo() throws Exception {
            // Given: 准备多实例执行和子任务数据
            setupMultiInstanceExecution(3, 2, 1);
            
            ExtendedTaskInfo task1 = createMultiInstanceTask("task-001", "user-001", "COMPLETED", 101L);
            task1.setCompletedBy("user-001");
            task1.setCompletedTime(LocalDateTime.now().minusHours(1));
            
            ExtendedTaskInfo task2 = createMultiInstanceTask("task-002", "user-002", "COMPLETED", 102L);
            task2.setCompletedBy("user-002");
            task2.setCompletedTime(LocalDateTime.now().minusMinutes(30));
            
            ExtendedTaskInfo task3 = createMultiInstanceTask("task-003", "user-003", "ASSIGNED", 103L);
            
            multiInstanceTasks = Arrays.asList(task1, task2, task3);
            when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(processInstanceId))
                    .thenReturn(multiInstanceTasks);
            
            mockObjectMapperForMultiInstance();
            
            // When: 调用查询接口
            ResponseEntity<ApiResponse<MultiInstanceStatusResponse>> response = controller.getStatus(processInstanceId);
            
            // Then: 验证子任务详情
            MultiInstanceStatusResponse data = response.getBody().getData();
            assertThat(data.getTasks()).hasSize(3);
            
            MultiInstanceStatusResponse.SubTaskDetail detail1 = data.getTasks().get(0);
            assertThat(detail1.getTaskId()).isEqualTo("task-001");
            assertThat(detail1.getAssignee()).isEqualTo("user-001");
            assertThat(detail1.getStatus()).isEqualTo("COMPLETED");
            assertThat(detail1.getSubTableRowId()).isEqualTo(101L);
            assertThat(detail1.getCompletedBy()).isEqualTo("user-001");
            assertThat(detail1.getCompletedTime()).isNotNull();
        }

        @Test
        @DisplayName("应该正确统计已取消的实例数")
        void shouldCountCancelledInstances() throws Exception {
            // Given: 准备包含已取消任务的数据
            setupMultiInstanceExecution(5, 3, 0);
            
            ExtendedTaskInfo task1 = createMultiInstanceTask("task-001", "user-001", "COMPLETED", 101L);
            ExtendedTaskInfo task2 = createMultiInstanceTask("task-002", "user-002", "COMPLETED", 102L);
            ExtendedTaskInfo task3 = createMultiInstanceTask("task-003", "user-003", "COMPLETED", 103L);
            ExtendedTaskInfo task4 = createMultiInstanceTask("task-004", "user-004", "CANCELLED", 104L);
            ExtendedTaskInfo task5 = createMultiInstanceTask("task-005", "user-005", "CANCELLED", 105L);
            
            multiInstanceTasks = Arrays.asList(task1, task2, task3, task4, task5);
            when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(processInstanceId))
                    .thenReturn(multiInstanceTasks);
            
            mockObjectMapperForMultiInstance();
            
            // When: 调用查询接口
            ResponseEntity<ApiResponse<MultiInstanceStatusResponse>> response = controller.getStatus(processInstanceId);
            
            // Then: 验证已取消实例数
            MultiInstanceStatusResponse data = response.getBody().getData();
            assertThat(data.getCancelledInstances()).isEqualTo(2);
            assertThat(data.getStatus()).isEqualTo("CANCELLED");
        }

        @Test
        @DisplayName("应该正确判断多实例状态为 COMPLETED")
        void shouldDetermineStatusAsCompleted() throws Exception {
            // Given: 所有实例都已完成
            setupMultiInstanceExecution(3, 3, 0);
            
            LocalDateTime now = LocalDateTime.now();
            ExtendedTaskInfo task1 = createMultiInstanceTask("task-001", "user-001", "COMPLETED", 101L);
            task1.setCompletedTime(now.minusHours(1));
            
            ExtendedTaskInfo task2 = createMultiInstanceTask("task-002", "user-002", "COMPLETED", 102L);
            task2.setCompletedTime(now.minusMinutes(30));
            
            ExtendedTaskInfo task3 = createMultiInstanceTask("task-003", "user-003", "COMPLETED", 103L);
            task3.setCompletedTime(now.minusMinutes(10));
            
            multiInstanceTasks = Arrays.asList(task1, task2, task3);
            when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(processInstanceId))
                    .thenReturn(multiInstanceTasks);
            
            mockObjectMapperForMultiInstance();
            
            // When: 调用查询接口
            ResponseEntity<ApiResponse<MultiInstanceStatusResponse>> response = controller.getStatus(processInstanceId);
            
            // Then: 验证状态为 COMPLETED
            MultiInstanceStatusResponse data = response.getBody().getData();
            assertThat(data.getStatus()).isEqualTo("COMPLETED");
            assertThat(data.getCompletedTime()).isNotNull();
        }

        @Test
        @DisplayName("应该正确设置开始和完成时间")
        void shouldSetStartAndCompletedTime() throws Exception {
            // Given: 准备带时间戳的任务数据
            setupMultiInstanceExecution(2, 2, 0);
            
            LocalDateTime now = LocalDateTime.now();
            ExtendedTaskInfo task1 = createMultiInstanceTask("task-001", "user-001", "COMPLETED", 101L);
            task1.setCreatedTime(now.minusHours(2));
            task1.setCompletedTime(now.minusHours(1));
            
            ExtendedTaskInfo task2 = createMultiInstanceTask("task-002", "user-002", "COMPLETED", 102L);
            task2.setCreatedTime(now.minusHours(2).plusMinutes(10));
            task2.setCompletedTime(now.minusMinutes(30));
            
            multiInstanceTasks = Arrays.asList(task1, task2);
            when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(processInstanceId))
                    .thenReturn(multiInstanceTasks);
            
            mockObjectMapperForMultiInstance();
            
            // When: 调用查询接口
            ResponseEntity<ApiResponse<MultiInstanceStatusResponse>> response = controller.getStatus(processInstanceId);
            
            // Then: 验证时间设置
            MultiInstanceStatusResponse data = response.getBody().getData();
            assertThat(data.getStartedTime()).isEqualTo(task1.getCreatedTime()); // 最早的创建时间
            assertThat(data.getCompletedTime()).isEqualTo(task2.getCompletedTime()); // 最晚的完成时间
        }
    }

    // ==================== 辅助方法 ====================

    private ExtendedTaskInfo createMultiInstanceTask(String taskId, String assignee, String status, Long rowId) {
        return ExtendedTaskInfo.builder()
                .taskId(taskId)
                .processInstanceId(processInstanceId)
                .processDefinitionId("process-def-001")
                .taskDefinitionKey("MI_UserTask_45")
                .taskName("补充个人信息")
                .assignmentType(AssignmentType.USER)
                .assignmentTarget(assignee)
                .status(status)
                .createdTime(LocalDateTime.now().minusHours(2))
                .extendedProperties("{\"multiInstance\":true,\"subTableName\":\"fu_participants\",\"subTableRowId\":" + rowId + "}")
                .build();
    }

    private void setupMultiInstanceExecution(int total, int completed, int active) {
        when(runtimeService.createExecutionQuery()).thenReturn(executionQuery);
        when(executionQuery.processInstanceId(processInstanceId)).thenReturn(executionQuery);
        
        List<Execution> executions = Arrays.asList(multiInstanceExecution);
        when(executionQuery.list()).thenReturn(executions);
        
        when(multiInstanceExecution.getId()).thenReturn("execution-001");
        when(multiInstanceExecution.getActivityId()).thenReturn("MultiInstance_SubTable_45");
        
        Map<String, Object> miVariables = new HashMap<>();
        miVariables.put("nrOfInstances", total);
        miVariables.put("nrOfCompletedInstances", completed);
        miVariables.put("nrOfActiveInstances", active);
        when(runtimeService.getVariables("execution-001")).thenReturn(miVariables);
    }

    private void mockObjectMapperForMultiInstance() throws Exception {
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenAnswer(invocation -> {
                    String json = invocation.getArgument(0);
                    Map<String, Object> props = new HashMap<>();
                    props.put("multiInstance", true);
                    props.put("subTableName", "fu_participants");
                    
                    // 简单解析 subTableRowId
                    if (json.contains("subTableRowId")) {
                        String[] parts = json.split("subTableRowId\":");
                        if (parts.length > 1) {
                            String numStr = parts[1].replaceAll("[^0-9]", "");
                            if (!numStr.isEmpty()) {
                                props.put("subTableRowId", Integer.parseInt(numStr));
                            }
                        }
                    }
                    
                    return props;
                });
    }
    
    @Nested
    @DisplayName("查询主任务子表数据")
    class GetSubTableDataTests {

        @Test
        @DisplayName("应该成功返回子表数据 - 包含所有行的 assignee 和 status")
        void shouldReturnSubTableDataWithAssigneeAndStatus() throws Exception {
            // Given: 准备任务和子表数据
            String taskId = "task-main-001";
            String subTableName = "fu_participants";
            
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskId(taskId)).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(task);
            when(task.getProcessInstanceId()).thenReturn(processInstanceId);
            
            // 准备流程变量（包含多实例集合变量）
            Map<String, Object> processVariables = new HashMap<>();
            List<Map<String, Object>> collectionData = new ArrayList<>();
            collectionData.add(createCollectionItem(101L, "user-001", 1L));
            collectionData.add(createCollectionItem(102L, "user-002", 1L));
            collectionData.add(createCollectionItem(103L, "user-003", 1L));
            processVariables.put("multiInstance_fu_participants_collection", collectionData);
            
            when(runtimeService.getVariables(processInstanceId)).thenReturn(processVariables);
            
            doReturn(createSubTableRow(101L, "张三", "138xxxx1234"))
                    .doReturn(createSubTableRow(102L, "李四", "138xxxx5678"))
                    .doReturn(createSubTableRow(103L, "王五", "138xxxx9012"))
                    .when(jdbcTemplate).queryForMap(anyString(), any(Object[].class));
            
            // 准备扩展任务信息
            ExtendedTaskInfo task1 = createMultiInstanceTask("task-001", "user-001", "COMPLETED", 101L);
            ExtendedTaskInfo task2 = createMultiInstanceTask("task-002", "user-002", "ASSIGNED", 102L);
            ExtendedTaskInfo task3 = createMultiInstanceTask("task-003", "user-003", "ASSIGNED", 103L);
            
            when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(processInstanceId))
                    .thenReturn(Arrays.asList(task1, task2, task3));
            
            mockObjectMapperForMultiInstance();
            
            // When: 调用查询接口
            ResponseEntity<ApiResponse<SubTableDataResponse>> response = controller.getSubTableData(taskId);
            
            // Then: 验证响应
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            
            SubTableDataResponse data = response.getBody().getData();
            assertThat(data).isNotNull();
            assertThat(data.getTaskId()).isEqualTo(taskId);
            assertThat(data.getSubTableName()).isEqualTo(subTableName);
            assertThat(data.getRows()).hasSize(3);
            
            // 验证第一行数据
            SubTableDataResponse.SubTableRow row1 = data.getRows().get(0);
            assertThat(row1.getId()).isEqualTo(101L);
            assertThat(row1.getAssignee()).isEqualTo("user-001");
            assertThat(row1.getStatus()).isEqualTo("COMPLETED");
            assertThat(row1.getData()).containsEntry("name", "张三");
            
            // 验证第二行数据
            SubTableDataResponse.SubTableRow row2 = data.getRows().get(1);
            assertThat(row2.getId()).isEqualTo(102L);
            assertThat(row2.getAssignee()).isEqualTo("user-002");
            assertThat(row2.getStatus()).isEqualTo("ASSIGNED");
        }

        @Test
        @DisplayName("应该返回错误 - 当任务不存在时")
        void shouldReturnError_WhenTaskNotFound() {
            // Given: 任务不存在
            String taskId = "non-existent-task";
            
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskId(taskId)).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(null);
            
            // When: 调用查询接口
            ResponseEntity<ApiResponse<SubTableDataResponse>> response = controller.getSubTableData(taskId);
            
            // Then: 验证返回错误
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getCode()).isEqualTo("TASK_NOT_FOUND");
        }

        @Test
        @DisplayName("应该返回错误 - 当未找到多实例配置时")
        void shouldReturnError_WhenMultiInstanceConfigNotFound() {
            // Given: 流程变量中没有多实例集合变量
            String taskId = "task-main-001";
            
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskId(taskId)).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(task);
            when(task.getProcessInstanceId()).thenReturn(processInstanceId);
            
            Map<String, Object> processVariables = new HashMap<>();
            processVariables.put("someOtherVariable", "value");
            when(runtimeService.getVariables(processInstanceId)).thenReturn(processVariables);
            
            // When: 调用查询接口
            ResponseEntity<ApiResponse<SubTableDataResponse>> response = controller.getSubTableData(taskId);
            
            // Then: 验证返回错误
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getCode()).isEqualTo("MULTI_INSTANCE_CONFIG_NOT_FOUND");
        }

        @Test
        @DisplayName("应该返回空列表 - 当集合变量为空时")
        void shouldReturnEmptyList_WhenCollectionIsEmpty() {
            // Given: 集合变量为空
            String taskId = "task-main-001";
            String subTableName = "fu_participants";
            
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskId(taskId)).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(task);
            when(task.getProcessInstanceId()).thenReturn(processInstanceId);
            
            Map<String, Object> processVariables = new HashMap<>();
            processVariables.put("multiInstance_fu_participants_collection", Collections.emptyList());
            when(runtimeService.getVariables(processInstanceId)).thenReturn(processVariables);
            
            // When: 调用查询接口
            ResponseEntity<ApiResponse<SubTableDataResponse>> response = controller.getSubTableData(taskId);
            
            // Then: 验证返回空列表
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            
            SubTableDataResponse data = response.getBody().getData();
            assertThat(data.getTaskId()).isEqualTo(taskId);
            assertThat(data.getSubTableName()).isEqualTo(subTableName);
            assertThat(data.getRows()).isEmpty();
        }

        @Test
        @DisplayName("应该正确处理没有对应任务信息的数据行")
        void shouldHandleRowsWithoutTaskInfo() throws Exception {
            // Given: 部分数据行没有对应的任务信息
            String taskId = "task-main-001";
            
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.taskId(taskId)).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(task);
            when(task.getProcessInstanceId()).thenReturn(processInstanceId);
            
            Map<String, Object> processVariables = new HashMap<>();
            List<Map<String, Object>> collectionData = new ArrayList<>();
            collectionData.add(createCollectionItem(101L, "user-001", 1L));
            collectionData.add(createCollectionItem(102L, "user-002", 1L));
            processVariables.put("multiInstance_fu_participants_collection", collectionData);
            
            when(runtimeService.getVariables(processInstanceId)).thenReturn(processVariables);
            
            doReturn(createSubTableRow(101L, "张三", "138xxxx1234"))
                    .doReturn(createSubTableRow(102L, "李四", "138xxxx5678"))
                    .when(jdbcTemplate).queryForMap(anyString(), any(Object[].class));
            
            // 只有第一行有对应的任务信息
            ExtendedTaskInfo task1 = createMultiInstanceTask("task-001", "user-001", "COMPLETED", 101L);
            when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(processInstanceId))
                    .thenReturn(Collections.singletonList(task1));
            
            mockObjectMapperForMultiInstance();
            
            // When: 调用查询接口
            ResponseEntity<ApiResponse<SubTableDataResponse>> response = controller.getSubTableData(taskId);
            
            // Then: 验证响应
            SubTableDataResponse data = response.getBody().getData();
            assertThat(data.getRows()).hasSize(2);
            
            // 第一行有任务信息
            assertThat(data.getRows().get(0).getStatus()).isEqualTo("COMPLETED");
            
            // 第二行没有任务信息，状态应为 PENDING
            assertThat(data.getRows().get(1).getStatus()).isEqualTo("PENDING");
        }
        
        // ==================== 辅助方法 ====================
        
        private Map<String, Object> createCollectionItem(Long rowId, String assigneeId, Long rowVersion) {
            Map<String, Object> item = new HashMap<>();
            item.put("rowId", rowId);
            item.put("assigneeId", assigneeId);
            item.put("rowVersion", rowVersion);
            return item;
        }
        
        private Map<String, Object> createSubTableRow(Long id, String name, String phone) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", id);
            row.put("name", name);
            row.put("phone", phone);
            return row;
        }
    }
}
