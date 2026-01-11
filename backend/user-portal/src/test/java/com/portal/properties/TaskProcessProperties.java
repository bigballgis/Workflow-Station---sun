package com.portal.properties;

import com.portal.component.TaskProcessComponent;
import com.portal.component.TaskQueryComponent;
import com.portal.dto.TaskCompleteRequest;
import com.portal.dto.TaskInfo;
import com.portal.entity.DelegationRule;
import com.portal.enums.DelegationStatus;
import com.portal.enums.DelegationType;
import com.portal.exception.PortalException;
import com.portal.repository.DelegationAuditRepository;
import com.portal.repository.DelegationRuleRepository;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 任务处理权限属性测试
 * 验证只有有权限的用户才能处理任务
 */
class TaskProcessProperties {

    @Mock
    private DelegationRuleRepository delegationRuleRepository;

    @Mock
    private DelegationAuditRepository delegationAuditRepository;

    @Mock
    private ProcessInstanceRepository processInstanceRepository;

    private TaskQueryComponent taskQueryComponent;
    private TaskProcessComponent taskProcessComponent;
    private Random random;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        taskQueryComponent = new TaskQueryComponent(delegationRuleRepository, processInstanceRepository);
        taskProcessComponent = new TaskProcessComponent(taskQueryComponent, delegationRuleRepository, delegationAuditRepository);
        taskQueryComponent.clearTasks();
        random = new Random();

        // 默认返回空委托列表
        when(delegationRuleRepository.findActiveDelegationsForDelegate(any(), any()))
                .thenReturn(Collections.emptyList());
    }

    /**
     * 属性1: 直接分配的任务只有被分配人可以处理
     */
    @RepeatedTest(20)
    void directAssignedTaskCanOnlyBeProcessedByAssignee() {
        String assignee = "user_" + random.nextInt(1000);
        String otherUser = "other_" + random.nextInt(1000);
        String taskId = "task_" + random.nextInt(1000);

        TaskInfo task = createTask(taskId, "USER", assignee);
        taskQueryComponent.addTask(task);

        // 被分配人可以处理
        assertTrue(taskProcessComponent.canProcessTask(task, assignee));

        // 其他用户不能处理
        assertFalse(taskProcessComponent.canProcessTask(task, otherUser));
    }

    /**
     * 属性2: 虚拟组任务可以被组成员认领和处理
     */
    @RepeatedTest(20)
    void virtualGroupTaskCanBeClaimedByGroupMember() {
        String userId = "user_" + random.nextInt(1000);
        String groupId = "group_" + userId; // 模拟用户所属的组
        String taskId = "task_" + random.nextInt(1000);

        TaskInfo task = createTask(taskId, "VIRTUAL_GROUP", groupId);
        taskQueryComponent.addTask(task);

        // 组成员可以认领
        assertTrue(taskProcessComponent.canClaimTask(task, userId));

        // 组成员可以处理
        assertTrue(taskProcessComponent.canProcessTask(task, userId));
    }

    /**
     * 属性3: 部门角色任务可以被符合条件的用户认领和处理
     */
    @RepeatedTest(20)
    void deptRoleTaskCanBeClaimedByQualifiedUser() {
        String userId = "user_" + random.nextInt(1000);
        String deptRoleId = "dept_role_" + userId; // 模拟用户的部门角色
        String taskId = "task_" + random.nextInt(1000);

        TaskInfo task = createTask(taskId, "DEPT_ROLE", deptRoleId);
        taskQueryComponent.addTask(task);

        // 符合条件的用户可以认领
        assertTrue(taskProcessComponent.canClaimTask(task, userId));

        // 符合条件的用户可以处理
        assertTrue(taskProcessComponent.canProcessTask(task, userId));
    }

    /**
     * 属性4: 委托任务可以被委托人处理
     */
    @RepeatedTest(20)
    void delegatedTaskCanBeProcessedByDelegate() {
        String delegatorId = "delegator_" + random.nextInt(1000);
        String delegateId = "delegate_" + random.nextInt(1000);
        String taskId = "task_" + random.nextInt(1000);

        // 设置委托规则
        DelegationRule rule = DelegationRule.builder()
                .delegatorId(delegatorId)
                .delegateId(delegateId)
                .delegationType(DelegationType.ALL)
                .status(DelegationStatus.ACTIVE)
                .build();
        when(delegationRuleRepository.findActiveDelegationsForDelegate(eq(delegateId), any()))
                .thenReturn(List.of(rule));

        TaskInfo task = createTask(taskId, "USER", delegatorId);
        taskQueryComponent.addTask(task);

        // 委托人可以处理
        assertTrue(taskProcessComponent.canProcessTask(task, delegateId));
    }

    /**
     * 属性5: 认领任务后分配类型变为USER
     */
    @RepeatedTest(20)
    void claimingTaskChangesAssignmentTypeToUser() {
        String userId = "user_" + random.nextInt(1000);
        String groupId = "group_" + userId;
        String taskId = "task_" + random.nextInt(1000);

        TaskInfo task = createTask(taskId, "VIRTUAL_GROUP", groupId);
        taskQueryComponent.addTask(task);

        TaskInfo claimedTask = taskProcessComponent.claimTask(taskId, userId);

        assertEquals("USER", claimedTask.getAssignmentType());
        assertEquals(userId, claimedTask.getAssignee());
    }

    /**
     * 属性6: 直接分配的任务不能被认领
     */
    @RepeatedTest(20)
    void directAssignedTaskCannotBeClaimed() {
        String assignee = "user_" + random.nextInt(1000);
        String taskId = "task_" + random.nextInt(1000);

        TaskInfo task = createTask(taskId, "USER", assignee);
        taskQueryComponent.addTask(task);

        assertThrows(PortalException.class, () -> 
            taskProcessComponent.claimTask(taskId, assignee));
    }

    /**
     * 属性7: 无权限用户不能认领任务
     */
    @RepeatedTest(20)
    void unauthorizedUserCannotClaimTask() {
        String groupId = "group_specific";
        String unauthorizedUser = "unauthorized_" + random.nextInt(1000);
        String taskId = "task_" + random.nextInt(1000);

        TaskInfo task = createTask(taskId, "VIRTUAL_GROUP", groupId);
        taskQueryComponent.addTask(task);

        assertThrows(PortalException.class, () -> 
            taskProcessComponent.claimTask(taskId, unauthorizedUser));
    }

    /**
     * 属性8: 委托任务后委托人信息被正确记录
     */
    @RepeatedTest(20)
    void delegatingTaskRecordsDelegatorInfo() {
        String delegatorId = "delegator_" + random.nextInt(1000);
        String delegateId = "delegate_" + random.nextInt(1000);
        String taskId = "task_" + random.nextInt(1000);

        TaskInfo task = createTask(taskId, "USER", delegatorId);
        taskQueryComponent.addTask(task);

        taskProcessComponent.delegateTask(taskId, delegatorId, delegateId, "出差委托");

        TaskInfo delegatedTask = taskQueryComponent.getTaskById(taskId).orElseThrow();
        assertEquals("DELEGATED", delegatedTask.getAssignmentType());
        assertEquals(delegateId, delegatedTask.getAssignee());
        assertEquals(delegatorId, delegatedTask.getDelegatorId());
    }

    /**
     * 属性9: 转办任务后原分配信息被清除
     */
    @RepeatedTest(20)
    void transferringTaskClearsDelegationInfo() {
        String fromUserId = "from_" + random.nextInt(1000);
        String toUserId = "to_" + random.nextInt(1000);
        String taskId = "task_" + random.nextInt(1000);

        TaskInfo task = createTask(taskId, "USER", fromUserId);
        task.setDelegatorId("some_delegator");
        taskQueryComponent.addTask(task);

        taskProcessComponent.transferTask(taskId, fromUserId, toUserId, "工作交接");

        TaskInfo transferredTask = taskQueryComponent.getTaskById(taskId).orElseThrow();
        assertEquals("USER", transferredTask.getAssignmentType());
        assertEquals(toUserId, transferredTask.getAssignee());
        assertNull(transferredTask.getDelegatorId());
    }

    /**
     * 属性10: 无权限用户不能完成任务
     */
    @RepeatedTest(20)
    void unauthorizedUserCannotCompleteTask() {
        String assignee = "user_" + random.nextInt(1000);
        String unauthorizedUser = "unauthorized_" + random.nextInt(1000);
        String taskId = "task_" + random.nextInt(1000);

        TaskInfo task = createTask(taskId, "USER", assignee);
        taskQueryComponent.addTask(task);

        TaskCompleteRequest request = TaskCompleteRequest.builder()
                .taskId(taskId)
                .action("APPROVE")
                .comment("同意")
                .build();

        assertThrows(PortalException.class, () -> 
            taskProcessComponent.completeTask(request, unauthorizedUser));
    }

    /**
     * 属性11: 不存在的任务操作应该抛出异常
     */
    @Test
    void operationOnNonexistentTaskShouldThrowException() {
        String taskId = "nonexistent_task";
        String userId = "user_1";

        assertThrows(PortalException.class, () -> 
            taskProcessComponent.claimTask(taskId, userId));

        TaskCompleteRequest request = TaskCompleteRequest.builder()
                .taskId(taskId)
                .action("APPROVE")
                .build();
        assertThrows(PortalException.class, () -> 
            taskProcessComponent.completeTask(request, userId));
    }

    /**
     * 属性12: 委托和转办需要目标用户
     */
    @RepeatedTest(20)
    void delegateAndTransferRequireTargetUser() {
        String userId = "user_" + random.nextInt(1000);
        String taskId = "task_" + random.nextInt(1000);

        TaskInfo task = createTask(taskId, "USER", userId);
        taskQueryComponent.addTask(task);

        // 委托没有目标用户
        TaskCompleteRequest delegateRequest = TaskCompleteRequest.builder()
                .taskId(taskId)
                .action("DELEGATE")
                .build();
        assertThrows(PortalException.class, () -> 
            taskProcessComponent.completeTask(delegateRequest, userId));

        // 转办没有目标用户
        TaskCompleteRequest transferRequest = TaskCompleteRequest.builder()
                .taskId(taskId)
                .action("TRANSFER")
                .build();
        assertThrows(PortalException.class, () -> 
            taskProcessComponent.completeTask(transferRequest, userId));
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
