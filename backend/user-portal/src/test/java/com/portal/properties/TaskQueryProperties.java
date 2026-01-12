package com.portal.properties;

import com.portal.client.WorkflowEngineClient;
import com.portal.component.TaskQueryComponent;
import com.portal.dto.PageResponse;
import com.portal.dto.TaskInfo;
import com.portal.dto.TaskQueryRequest;
import com.portal.entity.DelegationRule;
import com.portal.enums.DelegationStatus;
import com.portal.enums.DelegationType;
import com.portal.repository.DelegationRuleRepository;
import com.portal.repository.ProcessHistoryRepository;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 多维度任务查询属性测试
 * 验证任务查询结果的完整性和正确性
 * 
 * 注意：TaskQueryComponent 现在通过 WorkflowEngineClient 从 Flowable 获取任务
 */
class TaskQueryProperties {

    @Mock
    private DelegationRuleRepository delegationRuleRepository;

    @Mock
    private ProcessInstanceRepository processInstanceRepository;
    
    @Mock
    private ProcessHistoryRepository processHistoryRepository;
    
    @Mock
    private WorkflowEngineClient workflowEngineClient;

    private TaskQueryComponent taskQueryComponent;
    private Random random;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        taskQueryComponent = new TaskQueryComponent(
            delegationRuleRepository, 
            processInstanceRepository, 
            processHistoryRepository,
            workflowEngineClient
        );
        random = new Random();
        
        // 默认 Flowable 引擎可用
        when(workflowEngineClient.isAvailable()).thenReturn(true);
        
        // 默认返回空委托列表
        when(delegationRuleRepository.findActiveDelegationsForDelegate(any(), any()))
                .thenReturn(Collections.emptyList());
        
        // Mock 用户权限查询 - 返回包含虚拟组和部门角色的权限信息
        // 使用 Answer 动态生成权限数据，使虚拟组和部门角色与用户ID关联
        when(workflowEngineClient.getUserTaskPermissions(anyString()))
                .thenAnswer(invocation -> {
                    String userId = invocation.getArgument(0);
                    Map<String, Object> permissions = new HashMap<>();
                    // 虚拟组ID格式: group_<userId>
                    permissions.put("virtualGroupIds", List.of("group_" + userId));
                    // 部门角色ID格式: dept_role_<userId>
                    permissions.put("departmentRoles", List.of("dept_role_" + userId));
                    return Optional.of(permissions);
                });
    }

    /**
     * 属性1: 直接分配给用户的任务应该被正确查询
     */
    @RepeatedTest(20)
    void directAssignedTasksShouldBeQueried() {
        String userId = "user_" + random.nextInt(1000);
        int taskCount = 1 + random.nextInt(10);

        // 创建直接分配的任务数据
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            tasks.add(createTaskMap("task_" + i, "USER", userId));
        }
        
        // Mock Flowable 返回
        mockFlowableTasksResponse(tasks);

        TaskQueryRequest request = TaskQueryRequest.builder()
                .userId(userId)
                .assignmentTypes(List.of("USER"))
                .build();

        PageResponse<TaskInfo> result = taskQueryComponent.queryTasks(request);

        assertEquals(taskCount, result.getTotalElements());
        assertTrue(result.getContent().stream()
                .allMatch(t -> userId.equals(t.getAssignee())));
    }

    /**
     * 属性2: 虚拟组任务应该对组成员可见
     */
    @RepeatedTest(20)
    void virtualGroupTasksShouldBeVisibleToMembers() {
        String userId = "user_" + random.nextInt(1000);
        String groupId = "group_" + userId; // 模拟用户所属的组
        int taskCount = 1 + random.nextInt(10);

        // 创建虚拟组任务数据
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            tasks.add(createTaskMap("group_task_" + i, "VIRTUAL_GROUP", groupId));
        }
        
        // Mock Flowable 返回
        mockFlowableTasksResponse(tasks);

        TaskQueryRequest request = TaskQueryRequest.builder()
                .userId(userId)
                .assignmentTypes(List.of("VIRTUAL_GROUP"))
                .build();

        PageResponse<TaskInfo> result = taskQueryComponent.queryTasks(request);

        assertEquals(taskCount, result.getTotalElements());
        assertTrue(result.getContent().stream()
                .allMatch(t -> "VIRTUAL_GROUP".equals(t.getAssignmentType())));
    }

    /**
     * 属性3: 部门角色任务应该对符合条件的用户可见
     */
    @RepeatedTest(20)
    void deptRoleTasksShouldBeVisibleToQualifiedUsers() {
        String userId = "user_" + random.nextInt(1000);
        String deptRoleId = "dept_role_" + userId; // 模拟用户的部门角色
        int taskCount = 1 + random.nextInt(10);

        // 创建部门角色任务数据
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            tasks.add(createTaskMap("dept_task_" + i, "DEPT_ROLE", deptRoleId));
        }
        
        // Mock Flowable 返回
        mockFlowableTasksResponse(tasks);

        TaskQueryRequest request = TaskQueryRequest.builder()
                .userId(userId)
                .assignmentTypes(List.of("DEPT_ROLE"))
                .build();

        PageResponse<TaskInfo> result = taskQueryComponent.queryTasks(request);

        assertEquals(taskCount, result.getTotalElements());
        assertTrue(result.getContent().stream()
                .allMatch(t -> "DEPT_ROLE".equals(t.getAssignmentType())));
    }

    /**
     * 属性4: 委托任务应该对被委托人可见
     */
    @RepeatedTest(20)
    void delegatedTasksShouldBeVisibleToDelegate() {
        String delegatorId = "delegator_" + random.nextInt(1000);
        String delegateId = "delegate_" + random.nextInt(1000);
        int taskCount = 1 + random.nextInt(10);

        // 设置委托规则
        DelegationRule rule = DelegationRule.builder()
                .delegatorId(delegatorId)
                .delegateId(delegateId)
                .delegationType(DelegationType.ALL)
                .status(DelegationStatus.ACTIVE)
                .build();
        when(delegationRuleRepository.findActiveDelegationsForDelegate(eq(delegateId), any()))
                .thenReturn(List.of(rule));

        // 创建委托人的任务数据
        List<Map<String, Object>> delegatorTasks = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            delegatorTasks.add(createTaskMap("delegated_task_" + i, "USER", delegatorId));
        }
        
        // Mock Flowable 返回 - 被委托人自己没有任务
        mockFlowableTasksResponse(Collections.emptyList());
        
        // Mock 委托人的任务
        Map<String, Object> delegatorResponse = new HashMap<>();
        Map<String, Object> delegatorData = new HashMap<>();
        delegatorData.put("tasks", delegatorTasks);
        delegatorResponse.put("data", delegatorData);
        when(workflowEngineClient.getUserTasks(eq(delegatorId), anyInt(), anyInt()))
                .thenReturn(Optional.of(delegatorResponse));

        TaskQueryRequest request = TaskQueryRequest.builder()
                .userId(delegateId)
                .assignmentTypes(List.of("DELEGATED"))
                .build();

        PageResponse<TaskInfo> result = taskQueryComponent.queryTasks(request);

        assertEquals(taskCount, result.getTotalElements());
        assertTrue(result.getContent().stream()
                .allMatch(t -> "DELEGATED".equals(t.getAssignmentType())));
    }

    /**
     * 属性5: 多维度查询应该返回所有类型的任务（去重）
     */
    @RepeatedTest(20)
    void multiDimensionalQueryShouldReturnAllTypes() {
        String userId = "user_" + random.nextInt(1000);
        
        // 创建不同类型的任务数据
        List<Map<String, Object>> tasks = new ArrayList<>();
        tasks.add(createTaskMap("direct_task", "USER", userId));
        tasks.add(createTaskMap("group_task", "VIRTUAL_GROUP", "group_" + userId));
        tasks.add(createTaskMap("dept_task", "DEPT_ROLE", "dept_role_" + userId));
        
        // Mock Flowable 返回
        mockFlowableTasksResponse(tasks);

        TaskQueryRequest request = TaskQueryRequest.builder()
                .userId(userId)
                .build(); // 不指定类型，查询所有

        PageResponse<TaskInfo> result = taskQueryComponent.queryTasks(request);

        assertEquals(3, result.getTotalElements());
        
        Set<String> types = new HashSet<>();
        result.getContent().forEach(t -> types.add(t.getAssignmentType()));
        assertTrue(types.contains("USER"));
        assertTrue(types.contains("VIRTUAL_GROUP"));
        assertTrue(types.contains("DEPT_ROLE"));
    }

    /**
     * 属性6: 优先级筛选应该正确过滤任务
     */
    @RepeatedTest(20)
    void priorityFilterShouldWorkCorrectly() {
        String userId = "user_" + random.nextInt(1000);
        String[] priorities = {"URGENT", "HIGH", "NORMAL", "LOW"};
        
        // 创建不同优先级的任务数据
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (int i = 0; i < priorities.length; i++) {
            Map<String, Object> task = createTaskMap("task_" + i, "USER", userId);
            task.put("priority", priorities[i]);
            tasks.add(task);
        }
        
        // Mock Flowable 返回
        mockFlowableTasksResponse(tasks);

        // 只查询紧急和高优先级任务
        TaskQueryRequest request = TaskQueryRequest.builder()
                .userId(userId)
                .priorities(List.of("URGENT", "HIGH"))
                .build();

        PageResponse<TaskInfo> result = taskQueryComponent.queryTasks(request);

        assertEquals(2, result.getTotalElements());
        assertTrue(result.getContent().stream()
                .allMatch(t -> "URGENT".equals(t.getPriority()) || "HIGH".equals(t.getPriority())));
    }

    /**
     * 属性7: 分页应该正确工作
     */
    @RepeatedTest(20)
    void paginationShouldWorkCorrectly() {
        String userId = "user_" + random.nextInt(1000);
        int totalTasks = 25;
        int pageSize = 10;

        // 创建任务数据
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (int i = 0; i < totalTasks; i++) {
            tasks.add(createTaskMap("task_" + i, "USER", userId));
        }
        
        // Mock Flowable 返回
        mockFlowableTasksResponse(tasks);

        // 查询第一页
        TaskQueryRequest request1 = TaskQueryRequest.builder()
                .userId(userId)
                .page(0)
                .size(pageSize)
                .build();
        PageResponse<TaskInfo> result1 = taskQueryComponent.queryTasks(request1);

        assertEquals(pageSize, result1.getContent().size());
        assertEquals(totalTasks, result1.getTotalElements());
        assertEquals(3, result1.getTotalPages());
        assertTrue(result1.isHasNext());
        assertFalse(result1.isHasPrevious());

        // 查询最后一页
        TaskQueryRequest request2 = TaskQueryRequest.builder()
                .userId(userId)
                .page(2)
                .size(pageSize)
                .build();
        PageResponse<TaskInfo> result2 = taskQueryComponent.queryTasks(request2);

        assertEquals(5, result2.getContent().size()); // 25 - 20 = 5
        assertFalse(result2.isHasNext());
        assertTrue(result2.isHasPrevious());
    }

    /**
     * 属性8: 排序应该正确工作
     */
    @RepeatedTest(20)
    void sortingShouldWorkCorrectly() {
        String userId = "user_" + random.nextInt(1000);

        // 创建不同时间的任务数据
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> task = createTaskMap("task_" + i, "USER", userId);
            task.put("createdTime", LocalDateTime.now().minusDays(i));
            tasks.add(task);
        }
        
        // Mock Flowable 返回
        mockFlowableTasksResponse(tasks);

        // 按创建时间降序排序
        TaskQueryRequest request = TaskQueryRequest.builder()
                .userId(userId)
                .sortBy("createTime")
                .sortDirection("desc")
                .build();

        PageResponse<TaskInfo> result = taskQueryComponent.queryTasks(request);

        List<TaskInfo> resultTasks = result.getContent();
        for (int i = 0; i < resultTasks.size() - 1; i++) {
            assertTrue(resultTasks.get(i).getCreateTime().isAfter(resultTasks.get(i + 1).getCreateTime()) ||
                      resultTasks.get(i).getCreateTime().isEqual(resultTasks.get(i + 1).getCreateTime()));
        }
    }

    /**
     * 属性9: 关键词搜索应该正确过滤
     */
    @RepeatedTest(20)
    void keywordSearchShouldWorkCorrectly() {
        String userId = "user_" + random.nextInt(1000);
        String keyword = "请假";

        // 创建包含关键词的任务数据
        List<Map<String, Object>> tasks = new ArrayList<>();
        
        Map<String, Object> task1 = createTaskMap("task_1", "USER", userId);
        task1.put("taskName", "请假申请审批");
        tasks.add(task1);

        Map<String, Object> task2 = createTaskMap("task_2", "USER", userId);
        task2.put("taskName", "报销申请审批");
        tasks.add(task2);

        Map<String, Object> task3 = createTaskMap("task_3", "USER", userId);
        task3.put("taskDescription", "员工请假流程");
        tasks.add(task3);
        
        // Mock Flowable 返回
        mockFlowableTasksResponse(tasks);

        TaskQueryRequest request = TaskQueryRequest.builder()
                .userId(userId)
                .keyword(keyword)
                .build();

        PageResponse<TaskInfo> result = taskQueryComponent.queryTasks(request);

        assertEquals(2, result.getTotalElements());
        assertTrue(result.getContent().stream()
                .allMatch(t -> (t.getTaskName() != null && t.getTaskName().contains(keyword)) ||
                              (t.getDescription() != null && t.getDescription().contains(keyword))));
    }

    /**
     * 属性10: 空结果应该正确处理
     */
    @Test
    void emptyResultShouldBeHandledCorrectly() {
        String userId = "nonexistent_user";
        
        // Mock Flowable 返回空结果
        mockFlowableTasksResponse(Collections.emptyList());

        TaskQueryRequest request = TaskQueryRequest.builder()
                .userId(userId)
                .build();

        PageResponse<TaskInfo> result = taskQueryComponent.queryTasks(request);

        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
        assertFalse(result.isHasNext());
        assertFalse(result.isHasPrevious());
    }
    
    /**
     * 属性11: Flowable 引擎不可用时应该抛出异常
     */
    @Test
    void shouldThrowExceptionWhenFlowableUnavailable() {
        when(workflowEngineClient.isAvailable()).thenReturn(false);
        
        TaskQueryRequest request = TaskQueryRequest.builder()
                .userId("user_1")
                .build();

        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> taskQueryComponent.queryTasks(request));
        
        assertTrue(exception.getMessage().contains("Flowable 引擎不可用"));
    }

    /**
     * Mock Flowable 任务响应
     */
    private void mockFlowableTasksResponse(List<Map<String, Object>> tasks) {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        data.put("tasks", tasks);
        response.put("data", data);
        
        when(workflowEngineClient.getUserAllVisibleTasks(anyString(), anyList(), anyList(), anyInt(), anyInt()))
                .thenReturn(Optional.of(response));
        when(workflowEngineClient.getUserTasks(anyString(), anyInt(), anyInt()))
                .thenReturn(Optional.of(response));
    }

    /**
     * 创建测试任务 Map
     */
    private Map<String, Object> createTaskMap(String taskId, String assignmentType, String assignee) {
        Map<String, Object> task = new HashMap<>();
        task.put("taskId", taskId);
        task.put("taskName", "测试任务 " + taskId);
        task.put("taskDescription", "任务描述");
        task.put("processInstanceId", "PI_" + taskId);
        task.put("processDefinitionId", "test_process");
        task.put("assignmentType", assignmentType);
        task.put("currentAssignee", assignee);
        task.put("priority", "NORMAL");
        task.put("status", "PENDING");
        task.put("createdTime", LocalDateTime.now());
        task.put("isOverdue", false);
        return task;
    }
}
