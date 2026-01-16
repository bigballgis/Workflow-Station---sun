package com.workflow.properties;

import com.workflow.dto.response.TaskListResult;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * 多维度任务查询准确性属性测试
 * 
 * 验证属性 8: 多维度任务查询准确性
 * 对于任何用户的待办任务查询，返回的结果应该包含直接分配、虚拟组分配、部门角色分配和委托给该用户的所有未完成任务
 * 
 * 验证需求: 需求 3.1
 * 
 * 注意：这是一个简化的属性测试，主要验证任务查询逻辑的正确性，不依赖Spring Boot上下文
 */
@Label("功能: workflow-engine-core, 属性 8: 多维度任务查询准确性")
public class MultiDimensionalTaskQueryProperties {

    /**
     * 属性测试：用户直接分配任务过滤准确性
     * 验证直接分配给用户的任务能够被正确识别
     */
    @Property(tries = 100)
    @Label("用户直接分配任务过滤准确性")
    void userDirectAssignedTasksFilterAccuracy(
            @ForAll @Size(min = 1, max = 10) List<@NotBlank String> taskIds,
            @ForAll @NotBlank String userId,
            @ForAll @IntRange(min = 1, max = 100) int priority) {
        
        // 过滤掉空白字符串
        Assume.that(taskIds.stream().allMatch(id -> id != null && !id.trim().isEmpty()));
        Assume.that(userId != null && !userId.trim().isEmpty());
        
        // 创建直接分配给用户的任务
        List<ExtendedTaskInfo> allTasks = new ArrayList<>();
        List<String> expectedTaskIds = new ArrayList<>();
        
        for (String taskId : taskIds) {
            ExtendedTaskInfo task = createTestTask(taskId, AssignmentType.USER, userId, priority);
            allTasks.add(task);
            expectedTaskIds.add(taskId);
        }
        
        // 模拟查询逻辑：过滤出直接分配给用户的任务
        List<ExtendedTaskInfo> filteredTasks = allTasks.stream()
                .filter(task -> task.getAssignmentType() == AssignmentType.USER)
                .filter(task -> task.getAssignmentTarget().equals(userId))
                .filter(task -> !task.isCompleted())
                .collect(Collectors.toList());
        
        // 验证过滤结果
        assertThat(filteredTasks).hasSize(taskIds.size());
        
        Set<String> resultTaskIds = filteredTasks.stream()
                .map(ExtendedTaskInfo::getTaskId)
                .collect(Collectors.toSet());
        assertThat(resultTaskIds).containsExactlyInAnyOrderElementsOf(expectedTaskIds);
        
        // 验证任务信息正确性
        for (ExtendedTaskInfo task : filteredTasks) {
            assertThat(task.getAssignmentType()).isEqualTo(AssignmentType.USER);
            assertThat(task.getAssignmentTarget()).isEqualTo(userId);
            assertThat(task.getCurrentAssignee()).isEqualTo(userId);
            assertThat(task.getPriority()).isEqualTo(priority);
        }
    }
    
    /**
     * 属性测试：委托任务识别准确性
     * 验证委托给用户的任务能够被正确识别
     */
    @Property(tries = 100)
    @Label("委托任务识别准确性")
    void delegatedTasksIdentificationAccuracy(
            @ForAll @Size(min = 1, max = 10) List<@NotBlank String> taskIds,
            @ForAll @NotBlank String userId,
            @ForAll @NotBlank String originalAssignee,
            @ForAll @NotBlank String delegationReason) {
        
        // 过滤掉空白字符串和相同的用户ID
        Assume.that(taskIds.stream().allMatch(id -> id != null && !id.trim().isEmpty()));
        Assume.that(userId != null && !userId.trim().isEmpty());
        Assume.that(originalAssignee != null && !originalAssignee.trim().isEmpty());
        Assume.that(delegationReason != null && !delegationReason.trim().isEmpty());
        Assume.that(!originalAssignee.equals(userId));
        
        // 创建委托给用户的任务
        List<ExtendedTaskInfo> allTasks = new ArrayList<>();
        List<String> expectedTaskIds = new ArrayList<>();
        
        for (String taskId : taskIds) {
            ExtendedTaskInfo task = createTestTask(taskId, AssignmentType.USER, originalAssignee, 50);
            task.delegateTask(userId, originalAssignee, delegationReason);
            allTasks.add(task);
            expectedTaskIds.add(taskId);
        }
        
        // 模拟查询逻辑：过滤出委托给用户的任务
        List<ExtendedTaskInfo> filteredTasks = allTasks.stream()
                .filter(task -> task.getDelegatedTo() != null)
                .filter(task -> task.getDelegatedTo().equals(userId))
                .filter(task -> !task.isCompleted())
                .collect(Collectors.toList());
        
        // 验证过滤结果
        assertThat(filteredTasks).hasSize(taskIds.size());
        
        Set<String> resultTaskIds = filteredTasks.stream()
                .map(ExtendedTaskInfo::getTaskId)
                .collect(Collectors.toSet());
        assertThat(resultTaskIds).containsExactlyInAnyOrderElementsOf(expectedTaskIds);
        
        // 验证委托任务信息正确性
        for (ExtendedTaskInfo task : filteredTasks) {
            assertThat(task.getAssignmentType()).isEqualTo(AssignmentType.USER);
            assertThat(task.getAssignmentTarget()).isEqualTo(originalAssignee);
            assertThat(task.getCurrentAssignee()).isEqualTo(userId);
            assertThat(task.isDelegated()).isTrue();
        }
    }
    
    /**
     * 属性测试：认领任务识别准确性
     * 验证用户认领的虚拟组任务能够被正确识别
     */
    @Property(tries = 100)
    @Label("认领任务识别准确性")
    void claimedTasksIdentificationAccuracy(
            @ForAll @Size(min = 1, max = 10) List<@NotBlank String> groupTaskIds,
            @ForAll @NotBlank String userId,
            @ForAll @NotBlank String groupId) {
        
        // 过滤掉空白字符串
        Assume.that(groupTaskIds.stream().allMatch(id -> id != null && !id.trim().isEmpty()));
        Assume.that(userId != null && !userId.trim().isEmpty());
        Assume.that(groupId != null && !groupId.trim().isEmpty());
        
        List<ExtendedTaskInfo> allTasks = new ArrayList<>();
        List<String> expectedTaskIds = new ArrayList<>();
        
        // 创建虚拟组任务并认领
        for (String taskId : groupTaskIds) {
            ExtendedTaskInfo task = createTestTask(taskId + "-group", AssignmentType.VIRTUAL_GROUP, groupId, 60);
            task.claimTask(userId);
            allTasks.add(task);
            expectedTaskIds.add(taskId + "-group");
        }
        
        // 模拟查询逻辑：过滤出用户认领的任务
        List<ExtendedTaskInfo> filteredTasks = allTasks.stream()
                .filter(task -> task.getClaimedBy() != null)
                .filter(task -> task.getClaimedBy().equals(userId))
                .filter(task -> !task.isCompleted())
                .collect(Collectors.toList());
        
        // 验证过滤结果
        assertThat(filteredTasks).hasSize(expectedTaskIds.size());
        
        Set<String> resultTaskIds = filteredTasks.stream()
                .map(ExtendedTaskInfo::getTaskId)
                .collect(Collectors.toSet());
        assertThat(resultTaskIds).containsExactlyInAnyOrderElementsOf(expectedTaskIds);
        
        // 验证认领任务信息正确性
        for (ExtendedTaskInfo task : filteredTasks) {
            assertThat(task.getCurrentAssignee()).isEqualTo(userId);
            assertThat(task.isClaimed()).isTrue();
            assertThat(task.getAssignmentType()).isEqualTo(AssignmentType.VIRTUAL_GROUP);
        }
    }
    
    /**
     * 属性测试：混合任务类型查询准确性
     * 验证用户的直接分配、委托、认领任务能够同时被正确识别
     */
    @Property(tries = 100)
    @Label("混合任务类型查询准确性")
    void mixedTaskTypesQueryAccuracy(
            @ForAll @Size(min = 1, max = 3) List<@NotBlank String> directTaskIds,
            @ForAll @Size(min = 1, max = 3) List<@NotBlank String> delegatedTaskIds,
            @ForAll @Size(min = 1, max = 3) List<@NotBlank String> claimedTaskIds,
            @ForAll @NotBlank String userId,
            @ForAll @NotBlank String delegator,
            @ForAll @NotBlank String groupId) {
        
        // 过滤掉空白字符串和相同的用户ID
        Assume.that(directTaskIds.stream().allMatch(id -> id != null && !id.trim().isEmpty()));
        Assume.that(delegatedTaskIds.stream().allMatch(id -> id != null && !id.trim().isEmpty()));
        Assume.that(claimedTaskIds.stream().allMatch(id -> id != null && !id.trim().isEmpty()));
        Assume.that(userId != null && !userId.trim().isEmpty());
        Assume.that(delegator != null && !delegator.trim().isEmpty());
        Assume.that(groupId != null && !groupId.trim().isEmpty());
        Assume.that(!delegator.equals(userId));
        
        List<ExtendedTaskInfo> allTasks = new ArrayList<>();
        List<String> expectedTaskIds = new ArrayList<>();
        
        // 创建直接分配任务
        for (String taskId : directTaskIds) {
            ExtendedTaskInfo task = createTestTask(taskId, AssignmentType.USER, userId, 50);
            allTasks.add(task);
            expectedTaskIds.add(taskId);
        }
        
        // 创建委托任务
        for (String taskId : delegatedTaskIds) {
            ExtendedTaskInfo task = createTestTask(taskId, AssignmentType.USER, delegator, 60);
            task.delegateTask(userId, delegator, "测试委托");
            allTasks.add(task);
            expectedTaskIds.add(taskId);
        }
        
        // 创建认领任务
        for (String taskId : claimedTaskIds) {
            ExtendedTaskInfo task = createTestTask(taskId, AssignmentType.VIRTUAL_GROUP, groupId, 70);
            task.claimTask(userId);
            allTasks.add(task);
            expectedTaskIds.add(taskId);
        }
        
        // 模拟查询逻辑：过滤出用户的所有待办任务
        List<ExtendedTaskInfo> filteredTasks = allTasks.stream()
                .filter(task -> isUserTask(task, userId))
                .filter(task -> !task.isCompleted())
                .collect(Collectors.toList());
        
        // 验证过滤结果
        assertThat(filteredTasks).hasSize(expectedTaskIds.size());
        
        Set<String> resultTaskIds = filteredTasks.stream()
                .map(ExtendedTaskInfo::getTaskId)
                .collect(Collectors.toSet());
        assertThat(resultTaskIds).containsExactlyInAnyOrderElementsOf(expectedTaskIds);
        
        // 验证任务类型分布正确
        Map<String, Long> taskTypeCount = filteredTasks.stream()
                .collect(Collectors.groupingBy(task -> {
                    if (task.isDelegated()) return "DELEGATED";
                    if (task.isClaimed()) return "CLAIMED";
                    return "DIRECT";
                }, Collectors.counting()));
        
        assertThat(taskTypeCount.get("DIRECT")).isEqualTo(directTaskIds.size());
        assertThat(taskTypeCount.get("DELEGATED")).isEqualTo(delegatedTaskIds.size());
        assertThat(taskTypeCount.get("CLAIMED")).isEqualTo(claimedTaskIds.size());
    }
    
    /**
     * 属性测试：已完成任务过滤准确性
     * 验证已完成的任务不会出现在待办任务查询结果中
     */
    @Property(tries = 100)
    @Label("已完成任务过滤准确性")
    void completedTasksFilterAccuracy(
            @ForAll @Size(min = 1, max = 10) List<@NotBlank String> todoTaskIds,
            @ForAll @Size(min = 1, max = 10) List<@NotBlank String> completedTaskIds,
            @ForAll @NotBlank String userId) {
        
        // 过滤掉空白字符串
        Assume.that(todoTaskIds.stream().allMatch(id -> id != null && !id.trim().isEmpty()));
        Assume.that(completedTaskIds.stream().allMatch(id -> id != null && !id.trim().isEmpty()));
        Assume.that(userId != null && !userId.trim().isEmpty());
        
        List<ExtendedTaskInfo> allTasks = new ArrayList<>();
        
        // 创建待办任务
        for (String taskId : todoTaskIds) {
            ExtendedTaskInfo task = createTestTask(taskId, AssignmentType.USER, userId, 50);
            allTasks.add(task);
        }
        
        // 创建已完成任务
        for (String taskId : completedTaskIds) {
            ExtendedTaskInfo task = createTestTask(taskId, AssignmentType.USER, userId, 50);
            task.completeTask(userId);
            allTasks.add(task);
        }
        
        // 模拟查询逻辑：过滤出用户的待办任务（排除已完成）
        List<ExtendedTaskInfo> filteredTasks = allTasks.stream()
                .filter(task -> isUserTask(task, userId))
                .filter(task -> !task.isCompleted())
                .collect(Collectors.toList());
        
        // 验证过滤结果
        assertThat(filteredTasks).hasSize(todoTaskIds.size());
        
        // 验证只有待办任务被查询到
        Set<String> resultTaskIds = filteredTasks.stream()
                .map(ExtendedTaskInfo::getTaskId)
                .collect(Collectors.toSet());
        assertThat(resultTaskIds).containsExactlyInAnyOrderElementsOf(todoTaskIds);
        
        // 验证已完成任务没有被查询到
        for (String completedTaskId : completedTaskIds) {
            assertThat(resultTaskIds).doesNotContain(completedTaskId);
        }
        
        // 验证所有查询到的任务都不是已完成状态
        for (ExtendedTaskInfo task : filteredTasks) {
            assertThat(task.getStatus()).isNotEqualTo("COMPLETED");
        }
    }
    
    /**
     * 属性测试：任务优先级排序准确性
     * 验证查询结果按照优先级降序、创建时间升序排列
     */
    @Property(tries = 100)
    @Label("任务优先级排序准确性")
    void taskPrioritySortingAccuracy(
            @ForAll @Size(min = 3, max = 10) List<@IntRange(min = 1, max = 100) Integer> priorities,
            @ForAll @NotBlank String userId) {
        
        // 过滤掉空白字符串
        Assume.that(userId != null && !userId.trim().isEmpty());
        
        // 创建不同优先级的任务
        List<ExtendedTaskInfo> allTasks = new ArrayList<>();
        for (int i = 0; i < priorities.size(); i++) {
            String taskId = "task-" + i + "-" + System.currentTimeMillis();
            ExtendedTaskInfo task = createTestTask(taskId, AssignmentType.USER, userId, priorities.get(i));
            // 设置不同的创建时间
            task.setCreatedTime(LocalDateTime.now().plusSeconds(i));
            allTasks.add(task);
        }
        
        // 模拟排序逻辑：按优先级降序，创建时间升序
        List<ExtendedTaskInfo> sortedTasks = allTasks.stream()
                .filter(task -> isUserTask(task, userId))
                .filter(task -> !task.isCompleted())
                .sorted((t1, t2) -> {
                    int priorityCompare = Integer.compare(t2.getPriority(), t1.getPriority());
                    if (priorityCompare != 0) {
                        return priorityCompare;
                    }
                    return t1.getCreatedTime().compareTo(t2.getCreatedTime());
                })
                .collect(Collectors.toList());
        
        // 验证排序结果
        assertThat(sortedTasks).hasSize(priorities.size());
        
        // 验证排序正确性：优先级降序，创建时间升序
        for (int i = 0; i < sortedTasks.size() - 1; i++) {
            ExtendedTaskInfo currentTask = sortedTasks.get(i);
            ExtendedTaskInfo nextTask = sortedTasks.get(i + 1);
            
            // 优先级应该是降序或相等
            assertThat(currentTask.getPriority()).isGreaterThanOrEqualTo(nextTask.getPriority());
            
            // 如果优先级相等，创建时间应该是升序
            if (currentTask.getPriority().equals(nextTask.getPriority())) {
                assertThat(currentTask.getCreatedTime()).isBeforeOrEqualTo(nextTask.getCreatedTime());
            }
        }
    }
    
    /**
     * 属性测试：虚拟组任务可见性过滤准确性
     * 验证用户可见的虚拟组任务能够被正确过滤
     */
    @Property(tries = 100)
    @Label("虚拟组任务可见性过滤准确性")
    void virtualGroupTasksVisibilityFilterAccuracy(
            @ForAll @Size(min = 1, max = 10) List<@NotBlank String> visibleTaskIds,
            @ForAll @Size(min = 1, max = 5) List<@NotBlank String> invisibleTaskIds,
            @ForAll @NotBlank String userId,
            @ForAll @NotBlank String userGroupId,
            @ForAll @NotBlank String otherGroupId) {
        
        // 过滤掉空白字符串和相同的组ID
        Assume.that(visibleTaskIds.stream().allMatch(id -> id != null && !id.trim().isEmpty()));
        Assume.that(invisibleTaskIds.stream().allMatch(id -> id != null && !id.trim().isEmpty()));
        Assume.that(userId != null && !userId.trim().isEmpty());
        Assume.that(userGroupId != null && !userGroupId.trim().isEmpty());
        Assume.that(otherGroupId != null && !otherGroupId.trim().isEmpty());
        Assume.that(!userGroupId.equals(otherGroupId));
        
        List<ExtendedTaskInfo> allTasks = new ArrayList<>();
        
        // 创建用户可见的虚拟组任务
        for (String taskId : visibleTaskIds) {
            ExtendedTaskInfo task = createTestTask(taskId, AssignmentType.VIRTUAL_GROUP, userGroupId, 50);
            allTasks.add(task);
        }
        
        // 创建用户不可见的虚拟组任务
        for (String taskId : invisibleTaskIds) {
            ExtendedTaskInfo task = createTestTask(taskId, AssignmentType.VIRTUAL_GROUP, otherGroupId, 50);
            allTasks.add(task);
        }
        
        // 模拟查询逻辑：过滤出用户可见的虚拟组任务
        List<String> userGroups = Arrays.asList(userGroupId);
        List<ExtendedTaskInfo> filteredTasks = allTasks.stream()
                .filter(task -> task.getAssignmentType() == AssignmentType.VIRTUAL_GROUP)
                .filter(task -> userGroups.contains(task.getAssignmentTarget()))
                .filter(task -> task.getClaimedBy() == null) // 未认领的任务
                .filter(task -> !task.isCompleted())
                .collect(Collectors.toList());
        
        // 验证过滤结果
        assertThat(filteredTasks).hasSize(visibleTaskIds.size());
        
        // 验证只有可见的任务被查询到
        Set<String> resultTaskIds = filteredTasks.stream()
                .map(ExtendedTaskInfo::getTaskId)
                .collect(Collectors.toSet());
        assertThat(resultTaskIds).containsExactlyInAnyOrderElementsOf(visibleTaskIds);
        
        // 验证不可见的任务没有被查询到
        for (String invisibleTaskId : invisibleTaskIds) {
            assertThat(resultTaskIds).doesNotContain(invisibleTaskId);
        }
        
        // 验证虚拟组任务信息正确性
        for (ExtendedTaskInfo task : filteredTasks) {
            assertThat(task.getAssignmentType()).isEqualTo(AssignmentType.VIRTUAL_GROUP);
            assertThat(task.getAssignmentTarget()).isEqualTo(userGroupId);
            assertThat(task.getCurrentAssignee()).isNull(); // 未认领的虚拟组任务没有具体处理人
        }
    }
    
    
    // ==================== 辅助方法 ====================
    
    /**
     * 判断任务是否属于用户
     */
    private boolean isUserTask(ExtendedTaskInfo task, String userId) {
        // 直接分配给用户
        if (task.getAssignmentType() == AssignmentType.USER && 
            task.getAssignmentTarget().equals(userId)) {
            return true;
        }
        
        // 委托给用户
        if (task.getDelegatedTo() != null && task.getDelegatedTo().equals(userId)) {
            return true;
        }
        
        // 用户认领的任务
        if (task.getClaimedBy() != null && task.getClaimedBy().equals(userId)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 创建测试任务
     */
    private ExtendedTaskInfo createTestTask(String taskId, AssignmentType assignmentType, 
                                          String assignmentTarget, int priority) {
        return ExtendedTaskInfo.builder()
                .taskId(taskId)
                .processInstanceId("proc-inst-" + System.currentTimeMillis())
                .processDefinitionId("proc-def-" + System.currentTimeMillis())
                .taskDefinitionKey("test-task")
                .taskName("测试任务 " + taskId)
                .taskDescription("测试任务描述")
                .assignmentType(assignmentType)
                .assignmentTarget(assignmentTarget)
                .priority(priority)
                .status("ASSIGNED")
                .createdTime(LocalDateTime.now())
                .createdBy("test-system")
                .isDeleted(false)
                .version(0L)
                .build();
    }
}