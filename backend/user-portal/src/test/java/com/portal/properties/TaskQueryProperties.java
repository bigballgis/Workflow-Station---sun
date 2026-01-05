package com.portal.properties;

import com.portal.component.TaskQueryComponent;
import com.portal.dto.PageResponse;
import com.portal.dto.TaskInfo;
import com.portal.dto.TaskQueryRequest;
import com.portal.entity.DelegationRule;
import com.portal.enums.DelegationStatus;
import com.portal.enums.DelegationType;
import com.portal.repository.DelegationRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 多维度任务查询属性测试
 * 验证任务查询结果的完整性和正确性
 */
class TaskQueryProperties {

    @Mock
    private DelegationRuleRepository delegationRuleRepository;

    private TaskQueryComponent taskQueryComponent;
    private Random random;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        taskQueryComponent = new TaskQueryComponent(delegationRuleRepository);
        taskQueryComponent.clearTasks();
        random = new Random();
        
        // 默认返回空委托列表
        when(delegationRuleRepository.findActiveDelegationsForDelegate(any(), any()))
                .thenReturn(Collections.emptyList());
    }

    /**
     * 属性1: 直接分配给用户的任务应该被正确查询
     */
    @RepeatedTest(20)
    void directAssignedTasksShouldBeQueried() {
        String userId = "user_" + random.nextInt(1000);
        int taskCount = 1 + random.nextInt(10);

        // 创建直接分配的任务
        for (int i = 0; i < taskCount; i++) {
            TaskInfo task = createTask("task_" + i, "USER", userId);
            taskQueryComponent.addTask(task);
        }

        // 创建其他用户的任务
        for (int i = 0; i < 5; i++) {
            TaskInfo task = createTask("other_task_" + i, "USER", "other_user");
            taskQueryComponent.addTask(task);
        }

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

        // 创建虚拟组任务
        for (int i = 0; i < taskCount; i++) {
            TaskInfo task = createTask("group_task_" + i, "VIRTUAL_GROUP", groupId);
            taskQueryComponent.addTask(task);
        }

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

        // 创建部门角色任务
        for (int i = 0; i < taskCount; i++) {
            TaskInfo task = createTask("dept_task_" + i, "DEPT_ROLE", deptRoleId);
            taskQueryComponent.addTask(task);
        }

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

        // 创建委托人的任务
        for (int i = 0; i < taskCount; i++) {
            TaskInfo task = createTask("delegated_task_" + i, "USER", delegatorId);
            taskQueryComponent.addTask(task);
        }

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
        
        // 创建不同类型的任务
        TaskInfo directTask = createTask("direct_task", "USER", userId);
        TaskInfo groupTask = createTask("group_task", "VIRTUAL_GROUP", "group_" + userId);
        TaskInfo deptTask = createTask("dept_task", "DEPT_ROLE", "dept_role_" + userId);
        
        taskQueryComponent.addTask(directTask);
        taskQueryComponent.addTask(groupTask);
        taskQueryComponent.addTask(deptTask);

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
        
        // 创建不同优先级的任务
        for (int i = 0; i < priorities.length; i++) {
            TaskInfo task = createTask("task_" + i, "USER", userId);
            task.setPriority(priorities[i]);
            taskQueryComponent.addTask(task);
        }

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

        // 创建任务
        for (int i = 0; i < totalTasks; i++) {
            TaskInfo task = createTask("task_" + i, "USER", userId);
            taskQueryComponent.addTask(task);
        }

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

        // 创建不同时间的任务
        for (int i = 0; i < 5; i++) {
            TaskInfo task = createTask("task_" + i, "USER", userId);
            task.setCreateTime(LocalDateTime.now().minusDays(i));
            taskQueryComponent.addTask(task);
        }

        // 按创建时间降序排序
        TaskQueryRequest request = TaskQueryRequest.builder()
                .userId(userId)
                .sortBy("createTime")
                .sortDirection("desc")
                .build();

        PageResponse<TaskInfo> result = taskQueryComponent.queryTasks(request);

        List<TaskInfo> tasks = result.getContent();
        for (int i = 0; i < tasks.size() - 1; i++) {
            assertTrue(tasks.get(i).getCreateTime().isAfter(tasks.get(i + 1).getCreateTime()) ||
                      tasks.get(i).getCreateTime().isEqual(tasks.get(i + 1).getCreateTime()));
        }
    }

    /**
     * 属性9: 关键词搜索应该正确过滤
     */
    @RepeatedTest(20)
    void keywordSearchShouldWorkCorrectly() {
        String userId = "user_" + random.nextInt(1000);
        String keyword = "请假";

        // 创建包含关键词的任务
        TaskInfo task1 = createTask("task_1", "USER", userId);
        task1.setTaskName("请假申请审批");
        taskQueryComponent.addTask(task1);

        TaskInfo task2 = createTask("task_2", "USER", userId);
        task2.setTaskName("报销申请审批");
        taskQueryComponent.addTask(task2);

        TaskInfo task3 = createTask("task_3", "USER", userId);
        task3.setDescription("员工请假流程");
        taskQueryComponent.addTask(task3);

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
     * 创建测试任务
     */
    private TaskInfo createTask(String taskId, String assignmentType, String assignee) {
        return TaskInfo.builder()
                .taskId(taskId)
                .taskName("测试任务 " + taskId)
                .description("任务描述")
                .processInstanceId("PI_" + taskId)
                .processDefinitionKey("test_process")
                .processDefinitionName("测试流程")
                .assignmentType(assignmentType)
                .assignee(assignee)
                .priority("NORMAL")
                .status("PENDING")
                .createTime(LocalDateTime.now())
                .isOverdue(false)
                .build();
    }
}
