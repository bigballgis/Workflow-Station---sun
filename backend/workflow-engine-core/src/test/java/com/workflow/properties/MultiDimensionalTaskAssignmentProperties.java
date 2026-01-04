package com.workflow.properties;

import com.workflow.component.ProcessEngineComponent;
import com.workflow.component.TaskManagerComponent;
import com.workflow.dto.request.ProcessDefinitionRequest;
import com.workflow.dto.request.StartProcessRequest;
import com.workflow.dto.request.TaskAssignmentRequest;
import com.workflow.dto.request.TaskClaimRequest;
import com.workflow.dto.request.TaskDelegationRequest;
import com.workflow.dto.response.DeploymentResult;
import com.workflow.dto.response.ProcessInstanceResult;
import com.workflow.dto.response.TaskAssignmentResult;
import com.workflow.dto.response.TaskListResult;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import com.workflow.exception.WorkflowValidationException;
import com.workflow.repository.ExtendedTaskInfoRepository;

import net.jqwik.api.*;

import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * 多维度任务分配正确性属性测试
 * 验证需求: 需求 2.2, 3.2 - 多维度任务分配和管理
 * 
 * 属性 6: 多维度任务分配正确性
 * 对于任何有效的任务分配请求，系统应该：
 * 1. 正确创建或更新扩展任务信息
 * 2. 根据分配类型正确设置Flowable任务的分配信息
 * 3. 保证分配信息的一致性和完整性
 * 4. 支持用户、虚拟组、部门角色三种分配类型
 * 
 * 注意：由于jqwik与Spring Boot集成的复杂性，这里使用JUnit的@RepeatedTest来模拟属性测试
 * 每个测试方法会运行多次以验证属性的正确性
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class MultiDimensionalTaskAssignmentProperties {

    @Autowired
    private ProcessEngineComponent processEngineComponent;
    
    @Autowired
    private TaskManagerComponent taskManagerComponent;
    
    @Autowired
    private TaskService taskService;
    
    @Autowired
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;

    private static final String TEST_BPMN_WITH_USER_TASK = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xmlns:flowable="http://flowable.org/bpmn"
                     targetNamespace="http://www.flowable.org/processdef"
                     xsi:schemaLocation="http://www.omg.org/spec/BPMN/20100524/MODEL http://www.omg.org/spec/BPMN/20100524/BPMN20.xsd">
          <process id="%s" name="%s" isExecutable="true">
            <startEvent id="startEvent" name="Start"/>
            <sequenceFlow id="flow1" sourceRef="startEvent" targetRef="userTask"/>
            <userTask id="userTask" name="Test User Task"/>
            <sequenceFlow id="flow2" sourceRef="userTask" targetRef="endEvent"/>
            <endEvent id="endEvent" name="End"/>
          </process>
        </definitions>
        """;

    @BeforeEach
    void setUp() {
        // 确保每个测试都有干净的状态
        assertThat(processEngineComponent).isNotNull();
        assertThat(taskManagerComponent).isNotNull();
        assertThat(taskService).isNotNull();
        assertThat(extendedTaskInfoRepository).isNotNull();
    }

    /**
     * 属性测试: 用户分配类型的任务应该正确分配给指定用户
     * 功能: workflow-engine-core, 属性 6: 多维度任务分配正确性
     */
    @RepeatedTest(20)
    void userAssignmentShouldAssignTaskToSpecificUser() {
        // Given: 生成随机测试数据
        String userId = generateUserId();
        String operatorId = generateUserId();
        int priority = generatePriority();
        
        // Given: 创建一个流程实例和任务
        String taskId = createTestTaskInstance();
        
        TaskAssignmentRequest request = TaskAssignmentRequest.builder()
                .taskId(taskId)
                .assignmentType(AssignmentType.USER)
                .assignmentTarget(userId)
                .operatorUserId(operatorId)
                .priority(priority)
                .build();
        
        // When: 分配任务给用户
        TaskAssignmentResult result = taskManagerComponent.assignTask(taskId, request);
        
        // Then: 分配应该成功
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getAssignmentType()).isEqualTo(AssignmentType.USER);
        assertThat(result.getAssignmentTarget()).isEqualTo(userId);
        
        // 验证扩展任务信息
        Optional<ExtendedTaskInfo> extendedTaskInfo = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId);
        assertThat(extendedTaskInfo).isPresent();
        assertThat(extendedTaskInfo.get().getAssignmentType()).isEqualTo(AssignmentType.USER);
        assertThat(extendedTaskInfo.get().getAssignmentTarget()).isEqualTo(userId);
        assertThat(extendedTaskInfo.get().getPriority()).isEqualTo(priority);
        assertThat(extendedTaskInfo.get().getCurrentAssignee()).isEqualTo(userId);
        
        // 验证Flowable任务分配
        Task flowableTask = taskService.createTaskQuery().taskId(taskId).singleResult();
        assertThat(flowableTask).isNotNull();
        assertThat(flowableTask.getAssignee()).isEqualTo(userId);
        assertThat(flowableTask.getPriority()).isEqualTo(priority);
    }

    /**
     * 属性测试: 虚拟组分配类型的任务应该正确分配给虚拟组
     * 功能: workflow-engine-core, 属性 6: 多维度任务分配正确性
     */
    @RepeatedTest(20)
    void virtualGroupAssignmentShouldAssignTaskToGroup() {
        // Given: 生成随机测试数据
        String groupId = generateGroupId();
        String operatorId = generateUserId();
        int priority = generatePriority();
        
        // Given: 创建一个流程实例和任务
        String taskId = createTestTaskInstance();
        
        TaskAssignmentRequest request = TaskAssignmentRequest.builder()
                .taskId(taskId)
                .assignmentType(AssignmentType.VIRTUAL_GROUP)
                .assignmentTarget(groupId)
                .operatorUserId(operatorId)
                .priority(priority)
                .build();
        
        // When: 分配任务给虚拟组
        TaskAssignmentResult result = taskManagerComponent.assignTask(taskId, request);
        
        // Then: 分配应该成功
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getAssignmentType()).isEqualTo(AssignmentType.VIRTUAL_GROUP);
        assertThat(result.getAssignmentTarget()).isEqualTo(groupId);
        
        // 验证扩展任务信息
        Optional<ExtendedTaskInfo> extendedTaskInfo = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId);
        assertThat(extendedTaskInfo).isPresent();
        assertThat(extendedTaskInfo.get().getAssignmentType()).isEqualTo(AssignmentType.VIRTUAL_GROUP);
        assertThat(extendedTaskInfo.get().getAssignmentTarget()).isEqualTo(groupId);
        assertThat(extendedTaskInfo.get().getPriority()).isEqualTo(priority);
        assertThat(extendedTaskInfo.get().getCurrentAssignee()).isNull(); // 虚拟组任务没有具体处理人
        
        // 验证Flowable任务分配（虚拟组任务不应该有个人分配）
        Task flowableTask = taskService.createTaskQuery().taskId(taskId).singleResult();
        assertThat(flowableTask).isNotNull();
        assertThat(flowableTask.getAssignee()).isNull(); // 虚拟组任务清除个人分配
        assertThat(flowableTask.getPriority()).isEqualTo(priority);
    }

    /**
     * 属性测试: 部门角色分配类型的任务应该正确分配给部门角色
     * 功能: workflow-engine-core, 属性 6: 多维度任务分配正确性
     */
    @RepeatedTest(20)
    void deptRoleAssignmentShouldAssignTaskToDeptRole() {
        // Given: 生成随机测试数据
        String deptId = generateDeptId();
        String roleId = generateRoleId();
        String operatorId = generateUserId();
        int priority = generatePriority();
        
        // Given: 创建一个流程实例和任务
        String taskId = createTestTaskInstance();
        String deptRoleTarget = deptId + ":" + roleId;
        
        TaskAssignmentRequest request = TaskAssignmentRequest.builder()
                .taskId(taskId)
                .assignmentType(AssignmentType.DEPT_ROLE)
                .assignmentTarget(deptRoleTarget)
                .operatorUserId(operatorId)
                .priority(priority)
                .build();
        
        // When: 分配任务给部门角色
        TaskAssignmentResult result = taskManagerComponent.assignTask(taskId, request);
        
        // Then: 分配应该成功
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getAssignmentType()).isEqualTo(AssignmentType.DEPT_ROLE);
        assertThat(result.getAssignmentTarget()).isEqualTo(deptRoleTarget);
        
        // 验证扩展任务信息
        Optional<ExtendedTaskInfo> extendedTaskInfo = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId);
        assertThat(extendedTaskInfo).isPresent();
        assertThat(extendedTaskInfo.get().getAssignmentType()).isEqualTo(AssignmentType.DEPT_ROLE);
        assertThat(extendedTaskInfo.get().getAssignmentTarget()).isEqualTo(deptRoleTarget);
        assertThat(extendedTaskInfo.get().getPriority()).isEqualTo(priority);
        assertThat(extendedTaskInfo.get().getCurrentAssignee()).isNull(); // 部门角色任务没有具体处理人
        
        // 验证Flowable任务分配（部门角色任务不应该有个人分配）
        Task flowableTask = taskService.createTaskQuery().taskId(taskId).singleResult();
        assertThat(flowableTask).isNotNull();
        assertThat(flowableTask.getAssignee()).isNull(); // 部门角色任务清除个人分配
        assertThat(flowableTask.getPriority()).isEqualTo(priority);
    }

    /**
     * 属性测试: 任务重新分配应该清除之前的委托和认领信息
     * 功能: workflow-engine-core, 属性 6: 多维度任务分配正确性
     */
    @RepeatedTest(15)
    void taskReassignmentShouldClearPreviousDelegationAndClaim() {
        // Given: 生成随机测试数据
        String initialUserId = generateUserId();
        String delegatedUserId = generateUserId();
        String newUserId = generateUserId();
        String operatorId = generateUserId();
        
        // Given: 创建一个任务并进行初始分配和委托
        String taskId = createTestTaskInstance();
        
        // 初始分配给用户
        TaskAssignmentRequest initialRequest = TaskAssignmentRequest.builder()
                .taskId(taskId)
                .assignmentType(AssignmentType.USER)
                .assignmentTarget(initialUserId)
                .operatorUserId(operatorId)
                .build();
        
        taskManagerComponent.assignTask(taskId, initialRequest);
        
        // 委托给另一个用户
        TaskDelegationRequest delegationRequest = TaskDelegationRequest.builder()
                .taskId(taskId)
                .delegatedBy(initialUserId)
                .delegatedTo(delegatedUserId)
                .delegationReason("测试委托")
                .build();
        
        taskManagerComponent.delegateTask(taskId, delegationRequest);
        
        // When: 重新分配任务给新用户
        TaskAssignmentRequest reassignRequest = TaskAssignmentRequest.builder()
                .taskId(taskId)
                .assignmentType(AssignmentType.USER)
                .assignmentTarget(newUserId)
                .operatorUserId(operatorId)
                .build();
        
        TaskAssignmentResult result = taskManagerComponent.assignTask(taskId, reassignRequest);
        
        // Then: 重新分配应该成功，并清除委托信息
        assertThat(result.getSuccess()).isTrue();
        
        Optional<ExtendedTaskInfo> extendedTaskInfo = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId);
        assertThat(extendedTaskInfo).isPresent();
        
        ExtendedTaskInfo taskInfo = extendedTaskInfo.get();
        assertThat(taskInfo.getAssignmentType()).isEqualTo(AssignmentType.USER);
        assertThat(taskInfo.getAssignmentTarget()).isEqualTo(newUserId);
        assertThat(taskInfo.getCurrentAssignee()).isEqualTo(newUserId);
        
        // 验证委托信息已清除
        assertThat(taskInfo.getDelegatedTo()).isNull();
        assertThat(taskInfo.getDelegatedBy()).isNull();
        assertThat(taskInfo.getDelegationReason()).isNull();
        assertThat(taskInfo.getDelegatedTime()).isNull();
        
        // 验证认领信息已清除
        assertThat(taskInfo.getClaimedBy()).isNull();
        assertThat(taskInfo.getClaimedTime()).isNull();
    }

    /**
     * 属性测试: 虚拟组任务认领后应该变为用户分配
     * 功能: workflow-engine-core, 属性 6: 多维度任务分配正确性
     */
    @RepeatedTest(15)
    void virtualGroupTaskClaimShouldBecomeUserAssignment() {
        // Given: 生成随机测试数据
        String groupId = generateGroupId();
        String claimUserId = generateUserId();
        String operatorId = generateUserId();
        
        // Given: 创建一个虚拟组任务
        String taskId = createTestTaskInstance();
        
        TaskAssignmentRequest groupRequest = TaskAssignmentRequest.builder()
                .taskId(taskId)
                .assignmentType(AssignmentType.VIRTUAL_GROUP)
                .assignmentTarget(groupId)
                .operatorUserId(operatorId)
                .build();
        
        taskManagerComponent.assignTask(taskId, groupRequest);
        
        // When: 用户认领任务
        TaskClaimRequest claimRequest = TaskClaimRequest.builder()
                .taskId(taskId)
                .claimedBy(claimUserId)
                .build();
        
        TaskAssignmentResult result = taskManagerComponent.claimTask(taskId, claimRequest);
        
        // Then: 认领应该成功，任务变为用户分配
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getAssignmentType()).isEqualTo(AssignmentType.USER);
        assertThat(result.getAssignmentTarget()).isEqualTo(claimUserId);
        
        Optional<ExtendedTaskInfo> extendedTaskInfo = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId);
        assertThat(extendedTaskInfo).isPresent();
        
        ExtendedTaskInfo taskInfo = extendedTaskInfo.get();
        // 原始分配信息保持不变
        assertThat(taskInfo.getAssignmentType()).isEqualTo(AssignmentType.VIRTUAL_GROUP);
        assertThat(taskInfo.getAssignmentTarget()).isEqualTo(groupId);
        
        // 认领信息正确设置
        assertThat(taskInfo.getClaimedBy()).isEqualTo(claimUserId);
        assertThat(taskInfo.getClaimedTime()).isNotNull();
        assertThat(taskInfo.getCurrentAssignee()).isEqualTo(claimUserId);
        assertThat(taskInfo.isClaimed()).isTrue();
        
        // 验证Flowable任务分配
        Task flowableTask = taskService.createTaskQuery().taskId(taskId).singleResult();
        assertThat(flowableTask).isNotNull();
        assertThat(flowableTask.getAssignee()).isEqualTo(claimUserId);
    }

    /**
     * 属性测试: 部门角色任务认领后应该变为用户分配
     * 功能: workflow-engine-core, 属性 6: 多维度任务分配正确性
     */
    @RepeatedTest(15)
    void deptRoleTaskClaimShouldBecomeUserAssignment() {
        // Given: 生成随机测试数据
        String deptId = generateDeptId();
        String roleId = generateRoleId();
        String claimUserId = generateUserId();
        String operatorId = generateUserId();
        
        // Given: 创建一个部门角色任务
        String taskId = createTestTaskInstance();
        String deptRoleTarget = deptId + ":" + roleId;
        
        TaskAssignmentRequest deptRoleRequest = TaskAssignmentRequest.builder()
                .taskId(taskId)
                .assignmentType(AssignmentType.DEPT_ROLE)
                .assignmentTarget(deptRoleTarget)
                .operatorUserId(operatorId)
                .build();
        
        taskManagerComponent.assignTask(taskId, deptRoleRequest);
        
        // When: 用户认领任务
        TaskClaimRequest claimRequest = TaskClaimRequest.builder()
                .taskId(taskId)
                .claimedBy(claimUserId)
                .build();
        
        TaskAssignmentResult result = taskManagerComponent.claimTask(taskId, claimRequest);
        
        // Then: 认领应该成功，任务变为用户分配
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getAssignmentType()).isEqualTo(AssignmentType.USER);
        assertThat(result.getAssignmentTarget()).isEqualTo(claimUserId);
        
        Optional<ExtendedTaskInfo> extendedTaskInfo = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId);
        assertThat(extendedTaskInfo).isPresent();
        
        ExtendedTaskInfo taskInfo = extendedTaskInfo.get();
        // 原始分配信息保持不变
        assertThat(taskInfo.getAssignmentType()).isEqualTo(AssignmentType.DEPT_ROLE);
        assertThat(taskInfo.getAssignmentTarget()).isEqualTo(deptRoleTarget);
        
        // 认领信息正确设置
        assertThat(taskInfo.getClaimedBy()).isEqualTo(claimUserId);
        assertThat(taskInfo.getClaimedTime()).isNotNull();
        assertThat(taskInfo.getCurrentAssignee()).isEqualTo(claimUserId);
        assertThat(taskInfo.isClaimed()).isTrue();
        
        // 验证Flowable任务分配
        Task flowableTask = taskService.createTaskQuery().taskId(taskId).singleResult();
        assertThat(flowableTask).isNotNull();
        assertThat(flowableTask.getAssignee()).isEqualTo(claimUserId);
    }

    /**
     * 属性测试: 任务分配应该正确设置优先级和到期时间
     * 功能: workflow-engine-core, 属性 6: 多维度任务分配正确性
     */
    @RepeatedTest(15)
    void taskAssignmentShouldSetPriorityAndDueDate() {
        // Given: 生成随机测试数据
        String userId = generateUserId();
        String operatorId = generateUserId();
        int priority = generatePriority();
        
        // Given: 创建一个任务和分配请求（包含到期时间）
        String taskId = createTestTaskInstance();
        LocalDateTime dueDate = LocalDateTime.now().plusDays(7);
        
        TaskAssignmentRequest request = TaskAssignmentRequest.builder()
                .taskId(taskId)
                .assignmentType(AssignmentType.USER)
                .assignmentTarget(userId)
                .operatorUserId(operatorId)
                .priority(priority)
                .dueDate(dueDate)
                .build();
        
        // When: 分配任务
        TaskAssignmentResult result = taskManagerComponent.assignTask(taskId, request);
        
        // Then: 分配应该成功，优先级和到期时间正确设置
        assertThat(result.getSuccess()).isTrue();
        
        Optional<ExtendedTaskInfo> extendedTaskInfo = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId);
        assertThat(extendedTaskInfo).isPresent();
        
        ExtendedTaskInfo taskInfo = extendedTaskInfo.get();
        assertThat(taskInfo.getPriority()).isEqualTo(priority);
        assertThat(taskInfo.getDueDate()).isEqualTo(dueDate);
        
        // 验证Flowable任务的优先级和到期时间
        Task flowableTask = taskService.createTaskQuery().taskId(taskId).singleResult();
        assertThat(flowableTask).isNotNull();
        assertThat(flowableTask.getPriority()).isEqualTo(priority);
        assertThat(flowableTask.getDueDate()).isNotNull();
    }

    /**
     * 属性测试: 无效的分配请求应该被拒绝
     * 功能: workflow-engine-core, 属性 6: 多维度任务分配正确性
     */
    @Test
    void invalidAssignmentRequestShouldBeRejected() {
        // Given: 生成随机测试数据
        String operatorId = generateUserId();
        
        // Given: 创建一个任务
        String taskId = createTestTaskInstance();
        
        // When & Then: 测试各种无效的分配请求
        
        // 1. 空的分配目标
        TaskAssignmentRequest emptyTargetRequest = TaskAssignmentRequest.builder()
                .taskId(taskId)
                .assignmentType(AssignmentType.USER)
                .assignmentTarget("")
                .operatorUserId(operatorId)
                .build();
        
        assertThatThrownBy(() -> taskManagerComponent.assignTask(taskId, emptyTargetRequest))
                .isInstanceOf(WorkflowValidationException.class);
        
        // 2. 无效的部门角色格式
        TaskAssignmentRequest invalidDeptRoleRequest = TaskAssignmentRequest.builder()
                .taskId(taskId)
                .assignmentType(AssignmentType.DEPT_ROLE)
                .assignmentTarget("invalid_format")
                .operatorUserId(operatorId)
                .build();
        
        assertThatThrownBy(() -> taskManagerComponent.assignTask(taskId, invalidDeptRoleRequest))
                .isInstanceOf(WorkflowValidationException.class);
        
        // 3. 无效的优先级
        TaskAssignmentRequest invalidPriorityRequest = TaskAssignmentRequest.builder()
                .taskId(taskId)
                .assignmentType(AssignmentType.USER)
                .assignmentTarget("validUser")
                .operatorUserId(operatorId)
                .priority(150) // 超出范围
                .build();
        
        assertThatThrownBy(() -> taskManagerComponent.assignTask(taskId, invalidPriorityRequest))
                .isInstanceOf(WorkflowValidationException.class);
    }

    /**
     * 属性测试: 不存在的任务分配应该失败
     * 功能: workflow-engine-core, 属性 6: 多维度任务分配正确性
     */
    @Test
    void nonExistentTaskAssignmentShouldFail() {
        // Given: 生成随机测试数据
        String userId = generateUserId();
        String operatorId = generateUserId();
        
        // Given: 一个不存在的任务ID
        String nonExistentTaskId = "non_existent_task_" + UUID.randomUUID().toString();
        
        TaskAssignmentRequest request = TaskAssignmentRequest.builder()
                .taskId(nonExistentTaskId)
                .assignmentType(AssignmentType.USER)
                .assignmentTarget(userId)
                .operatorUserId(operatorId)
                .build();
        
        // When & Then: 分配不存在的任务应该失败
        assertThatThrownBy(() -> taskManagerComponent.assignTask(nonExistentTaskId, request))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("任务不存在");
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试用的任务实例
     */
    private String createTestTaskInstance() {
        try {
            // 创建流程定义
            String processKey = "testProcess_" + UUID.randomUUID().toString().substring(0, 8);
            String processName = "Test Process " + processKey;
            
            ProcessDefinitionRequest processRequest = new ProcessDefinitionRequest();
            processRequest.setName(processName);
            processRequest.setKey(processKey);
            processRequest.setCategory("test");
            processRequest.setBpmnXml(String.format(TEST_BPMN_WITH_USER_TASK, processKey, processName));
            
            DeploymentResult deploymentResult = processEngineComponent.deployProcess(processRequest);
            assertThat(deploymentResult.isSuccess()).isTrue();
            
            // 启动流程实例
            StartProcessRequest instanceRequest = new StartProcessRequest();
            instanceRequest.setProcessDefinitionKey(processKey);
            instanceRequest.setBusinessKey("test_" + UUID.randomUUID().toString().substring(0, 8));
            instanceRequest.setStartUserId("testUser");
            
            ProcessInstanceResult instanceResult = processEngineComponent.startProcess(instanceRequest);
            assertThat(instanceResult.isSuccess()).isTrue();
            
            // 获取任务ID
            List<Task> tasks = taskService.createTaskQuery()
                    .processInstanceId(instanceResult.getProcessInstanceId())
                    .list();
            
            assertThat(tasks).isNotEmpty();
            return tasks.get(0).getId();
            
        } catch (Exception e) {
            throw new RuntimeException("创建测试任务实例失败", e);
        }
    }

    // ==================== 随机数据生成器 ====================

    private final Random random = new Random();

    /**
     * 生成随机用户ID
     */
    private String generateUserId() {
        return "user_" + generateRandomString(8);
    }

    /**
     * 生成随机组ID
     */
    private String generateGroupId() {
        return "group_" + generateRandomString(8);
    }

    /**
     * 生成随机部门ID
     */
    private String generateDeptId() {
        return "dept_" + generateRandomString(8);
    }

    /**
     * 生成随机角色ID
     */
    private String generateRoleId() {
        return "role_" + generateRandomString(8);
    }

    /**
     * 生成随机优先级
     */
    private int generatePriority() {
        return random.nextInt(101); // 0-100
    }

    /**
     * 生成随机字符串
     */
    private String generateRandomString(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}